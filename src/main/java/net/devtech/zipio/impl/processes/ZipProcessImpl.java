package net.devtech.zipio.impl.processes;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.ZipTag;
import net.devtech.zipio.impl.InternalZipProcess;
import net.devtech.zipio.impl.ProcessingTransferHandler;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.ZipTagImpl;
import net.devtech.zipio.impl.util.Lazy;
import net.devtech.zipio.impl.util.U;
import net.devtech.zipio.processes.MemoryZipProcess;
import net.devtech.zipio.processes.ZipProcess;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.PostZipProcessor;
import net.devtech.zipio.processors.zip.ZipBehavior;
import net.devtech.zipio.processors.zip.ZipFilter;
import net.devtech.zipio.stage.TaskTransform;
import net.devtech.zipio.stage.ZipTransform;

public class ZipProcessImpl implements ZipProcessBuilder, InternalZipProcess {
	List<Path> processed = List.of();
	List<Runnable> afterExecute = List.of();
	List<Process<ZipProcess, UnaryOperator<Path>>> processes = List.of();
	List<Process<Path, Path>> zips = List.of();
	List<ZipTagImpl> tags = List.of();
	ZipEntryProcessor perEntry, postProcess;

	@Override
	public TaskTransform linkProcess(ZipProcess process, UnaryOperator<Path> newOutput) {
		var linked = new Process<>(process, newOutput);
		this.processes = U.add(this.processes, linked);
		return linked;
	}

	@Override
	public ZipTransform addZip(Path path, Path output) {
		var linked = new Process<>(path, output);
		this.zips = U.add(this.zips, linked);
		return linked;
	}

	@Override
	public ZipTag createZipTag(Path outputPath) {
		ZipTagImpl tag = new ZipTagImpl(outputPath);
		this.tags = U.add(this.tags, tag);
		return tag;
	}

	@Override
	public void addProcessed(Path output) {
		this.processed = U.add(this.processed, output);
	}

	@Override
	public void setEntryProcessor(ZipEntryProcessor processor) {
		this.perEntry = processor;
	}

	@Override
	public void setPostProcessor(ZipEntryProcessor handler) {
		this.postProcess = handler;
	}

	@Override
	public void afterExecute(Runnable runnable) {
		this.afterExecute = U.add(this.afterExecute, runnable);
	}

	@Override
	public void execute() throws IOException {
		Map<Path, FileSystem> toClose = new HashMap<>();
		this.execute(toClose, null);
		U.close(toClose);
	}

	@Override
	public MemoryZipProcess executeCached() throws IOException {
		Map<Path, FileSystem> toClose = new HashMap<>();
		// todo add validation to ensure process is closed whenever this is called?
		InMemoryZipProcessImpl process = new InMemoryZipProcessImpl(toClose);
		this.execute(toClose, process);
		return process;
	}

	@Override
	public Iterable<Path> getOutputs() {
		try {
			return this.getPaths();
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<Path, TransferHandler> handlerProvider) throws IOException {
		List<ToPostProcessPair> pairs = new ArrayList<>();
		List<VirtualZipEntry> afterAll = this.postProcess == null ? null : new Vector<>();
		for(ZipTagImpl tag : this.tags) {
			tag.handler = getHandler(toClose, handlerProvider, tag.output);
		}

		for(Iterator<Process<Path, Path>> iterator = this.zips.iterator(); iterator.hasNext(); ) {
			Process<Path, Path> zip = iterator.next();
			Path input = zip.a, output = zip.b;
			this.invokeFileIO(zip, handlerProvider, toClose, afterAll, input, output, zip.zipPre.apply(zip.a), zip.zipPost.apply(zip.a), pairs);
			iterator.remove();
		}

		for(Iterator<Process<ZipProcess, UnaryOperator<Path>>> iterator = this.processes.iterator(); iterator.hasNext(); ) {
			Process<ZipProcess, UnaryOperator<Path>> process = iterator.next(); // todo decide whether to pass output or input
			if(process.a instanceof InternalZipProcess i) {
				for(Path input : i.processed()) { // this must be first
					this.invokeFileIO(
							process,
							handlerProvider,
							toClose,
							afterAll,
							input,
							process.b.apply(input),
							process.zipPre.apply(input),
							process.zipPost.apply(input),
							pairs);
				}

				i.execute(toClose, input -> {
					// and here
					Path output = process.b.apply(input);
					ZipFilter filter = process.zipPre.apply(input);
					ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, Lazy.empty());
					if(behavior == ZipBehavior.CONTINUE) {
						TransferHandler transfer = getHandler(toClose, handlerProvider, output);
						if(output != null) {
							this.processed = U.add(this.processed, output);
						}
						if(transfer != null) {
							PostZipProcessor zipPost = process.zipPost.apply(input);
							if(zipPost != null) {
								pairs.add(new ToPostProcessPair(transfer, zipPost));
							}
							return new ProcessingTransferHandler(
									transfer,
									afterAll,
									this.perEntry,
									process.entryPost.apply(input),
									process.entryPre.apply(input),
									this.postProcess != null);
						} else {
							return null;
						}
					} else if(behavior == ZipBehavior.COPY) {
						this.processed = U.add(this.processed, input);
						return null;
					} else {
						return null;
					}
				});
			} else {
				process.a.execute();
				for(Path input : process.a.getOutputs()) {
					// filter needs to be placed here
					this.invokeFileIO(
							process,
							handlerProvider,
							toClose,
							afterAll,
							input,
							process.b.apply(input),
							process.zipPre.apply(input),
							process.zipPost.apply(input),
							pairs);
				}
			}
			iterator.remove();
		}

		if(afterAll != null) {
			for(VirtualZipEntry impl : afterAll) {
				ProcessResult result = this.postProcess.apply(impl);
				if(result == ProcessResult.POST) {
					throw new IllegalStateException(this.postProcess + " return POST in post handler! (final stage, can't post past final)");
				}
			}
		}

		for(ToPostProcessPair pair : pairs) {
			pair.processor.apply(pair.handler);
		}

		if(!this.processes.isEmpty()) {
			this.processes.clear();
		}

		this.afterExecute.forEach(Runnable::run);

		for(Iterator<ZipTagImpl> iterator = this.tags.iterator(); iterator.hasNext(); ) {
			ZipTagImpl tag = iterator.next();
			tag.handler = null;
			iterator.remove();
		}
	}

	@Override
	public Iterable<Path> processed() {
		return this.processed;
	}

	private static void visit(FileSystem in, TransferHandler handler) throws IOException {
		for(Path directory : in.getRootDirectories()) {
			Files.walk(directory).parallel().filter(Files::isRegularFile).forEach(path -> {
				String to = directory.relativize(path).toString();
				handler.copy(to, path);
			});
		}
	}

	private static TransferHandler getHandler(Map<Path, FileSystem> systems, Function<Path, TransferHandler> handlerProvider, Path output) {
		TransferHandler system = output == null ? null : new TransferHandler.System(systems.computeIfAbsent(output, U::createZip));
		if(handlerProvider != null) {
			TransferHandler handler = handlerProvider.apply(output);
			return handler == null ? system : handler.andThen(system);
		} else {
			return system;
		}
	}

	private List<Path> getPaths() throws IOException {
		List<Path> outputs = new ArrayList<>();
		for(ZipTagImpl tag : this.tags) {
			outputs.add(tag.output);
		}
		for(Process<Path, Path> zip : this.zips) {
			if(zip.b != null) {
				this.extracted(outputs, zip.zipPre.apply(zip.a), zip.a, zip.b, true);
			}
		}
		for(Process<ZipProcess, UnaryOperator<Path>> process : this.processes) {
			for(Path output : process.a.getOutputs()) {
				if(process.b != null) {
					this.extracted(outputs, process.zipPre.apply(output), output, process.b.apply(output), false);
				}
			}
		}
		outputs.addAll(this.processed);
		return outputs;
	}

	private void extracted(List<Path> outputs, ZipFilter processor, Path input, Path output, boolean tempSystem) throws IOException {
		Lazy<FileSystem> system = tempSystem ? new Lazy<>(() -> U.openZip(input)) : Lazy.empty();
		try {
			ZipBehavior behavior = processor == null ? ZipBehavior.CONTINUE : processor.test(output, system);
			if(behavior == ZipBehavior.CONTINUE) {
				outputs.add(output);
			} else if(behavior == ZipBehavior.COPY) {
				outputs.add(input);
			}
		} finally {
			if(system.supplier == null) {
				system.value.close();
			}
		}
	}

	private void invokeFileIO(Process<?, ?> linked,
			Function<Path, TransferHandler> handlerProvider,
			Map<Path, FileSystem> toClose,
			List<VirtualZipEntry> afterAll,
			Path input,
			Path output,
			ZipFilter filter,
			PostZipProcessor transform,
			List<ToPostProcessPair> pairs) {
		Lazy<FileSystem> inputSystem = new Lazy<>(() -> toClose.computeIfAbsent(input, U::openZip));
		ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, inputSystem);
		if(behavior == ZipBehavior.CONTINUE) {
			TransferHandler transfer = getHandler(toClose, handlerProvider, output);
			if(transfer != null) {
				try(ProcessingTransferHandler processing = new ProcessingTransferHandler(
						transfer,
						afterAll,
						this.perEntry,
						linked.entryPre.apply(input),
						linked.entryPost.apply(input),
						this.postProcess != null)) {
					visit(inputSystem.get(), processing);
				} catch(Exception e1) {
					throw U.rethrow(e1);
				}
				if(transform != null) {
					pairs.add(new ToPostProcessPair(transfer, transform));
				}
			}

			if(output != null) {
				this.processed = U.add(this.processed, output);
			}
		} else if(behavior == ZipBehavior.COPY) {
			this.processed = U.add(this.processed, input);
		}
	}

	record ToPostProcessPair(TransferHandler handler, PostZipProcessor processor) {}

	static class Process<A, B> implements TaskTransform, ZipTransform {
		final A a;
		final B b;
		Function<Path, ZipFilter> zipPre = p -> null;
		Function<Path, PostZipProcessor> zipPost = p -> null;
		Function<Path, ZipEntryProcessor> entryPost = p -> null, entryPre = p -> null;

		Process(A process, B newOutput) {
			this.a = process;
			this.b = newOutput;
		}

		@Override
		public void setZipFilter(Function<Path, ZipFilter> processor) {
			this.zipPre = processor;
		}

		@Override
		public void setPostZipProcessor(Function<Path, PostZipProcessor> processor) {
			this.zipPost = processor;
		}

		@Override
		public void setPostEntryProcessor(Function<Path, ZipEntryProcessor> processor) {
			this.entryPost = processor;
		}

		@Override
		public void setPreEntryProcessor(Function<Path, ZipEntryProcessor> processor) {
			this.entryPre = processor;
		}
	}
}
