package net.devtech.zipio.impl.processes;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import net.devtech.zipio.OutputTag;
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
	Process<Void, Void> defaults = new Process<>(null, null);
	List<OutputTag> processed = List.of();
	List<Runnable> afterExecute = List.of();
	List<Process<ZipProcess, UnaryOperator<OutputTag>>> processes = List.of();
	List<Process<OutputTag, OutputTag>> zips = List.of();
	List<ZipTagImpl> tags = List.of();
	List<AutoCloseable> closeables = List.of();
	ZipEntryProcessor perEntry, postProcess;

	@Override
	public TaskTransform defaults() {
		return this.defaults;
	}

	@Override
	public void addCloseable(Closeable closeable) {
		this.closeables = U.add(this.closeables, closeable);
	}

	@Override
	public TaskTransform linkProcess(ZipProcess process, UnaryOperator<OutputTag> newOutput) {
		var linked = new Process<>(process, newOutput);
		linked.loadDefaults(this.defaults);
		this.processes = U.add(this.processes, linked);
		return linked;
	}

	@Override
	public ZipTransform addZip(Path path, OutputTag output) {
		var linked = new Process<>(new OutputTag(path), output);
		linked.loadDefaults(this.defaults);
		this.zips = U.add(this.zips, linked);
		return linked;
	}

	@Override
	public ZipTag createZipTag(OutputTag outputPath) {
		ZipTagImpl tag = new ZipTagImpl(outputPath);
		this.tags = U.add(this.tags, tag);
		return tag;
	}

	@Override
	public void addProcessed(OutputTag output) {
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
		this.close();
	}

	@Override
	public MemoryZipProcess executeCached() throws IOException {
		Map<Path, FileSystem> toClose = new HashMap<>();
		// todo add validation to ensure process is closed whenever this is called?
		InMemoryZipProcessImpl process = new InMemoryZipProcessImpl(toClose);
		this.execute(toClose, process);
		this.close();
		return process;
	}

	@Override
	public Iterable<OutputTag> getOutputs() {
		try {
			return this.getPaths();
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<OutputTag, TransferHandler> handlerProvider) throws IOException {
		List<ToPostProcessPair> pairs = new ArrayList<>();
		List<VirtualZipEntry> afterAll = this.postProcess == null ? null : new Vector<>();
		for(ZipTagImpl tag : this.tags) {
			tag.handler = this.getHandler(toClose, handlerProvider, tag.getTag());
		}

		for(var iterator = this.zips.iterator(); iterator.hasNext(); ) {
			var zip = iterator.next();
			OutputTag output = zip.b, input = zip.a;
			this.invokeFileIO(zip, handlerProvider, toClose, afterAll, input, output, zip.zipPre.apply(zip.a), zip.zipPost.apply(zip.a), pairs);
			iterator.remove();
		}

		for(var iterator = this.processes.iterator(); iterator.hasNext(); ) {
			var process = iterator.next(); // todo decide whether to pass output or input
			if(process.a instanceof InternalZipProcess i) {
				for(OutputTag input : i.processed()) { // this must be first
					this.invokeFileIO(process,
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
					if(input == OutputTag.INPUT) {
						return null;
					}
					// and here
					ZipFilter filter = process.zipPre.apply(input);
					ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, Lazy.empty());
					if(behavior == ZipBehavior.CONTINUE) {
						OutputTag output = process.b.apply(input);
						TransferHandler transfer = this.getHandler(toClose, handlerProvider, output);
						this.processed = U.add(this.processed, output);
						if(transfer != null) {
							PostZipProcessor zipPost = process.zipPost.apply(input);
							if(zipPost != null) {
								pairs.add(new ToPostProcessPair(transfer, zipPost));
							}
							return new ProcessingTransferHandler(transfer,
									afterAll,
									this.perEntry,
									process.entryPost.apply(input),
									process.entryPre.apply(input),
									this.postProcess != null);
						} else {
							return null;
						}
					} else if(behavior == ZipBehavior.COPY) {
						this.copy(handlerProvider, toClose, input);
						return null;
					} else {
						return null;
					}
				});
			} else {
				process.a.execute();
				for(OutputTag input : process.a.getOutputs()) {
					// filter needs to be placed here
					this.invokeFileIO(process,
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

	// adding branches is going to be extremely painful
	// we need to account for "loops" n shit too irrrr painnn

	@Override
	public Iterable<OutputTag> processed() {
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

	private void close() throws IOException {
		for(AutoCloseable closeable : this.closeables) {
			try {
				closeable.close();
			} catch(Exception e) {
				throw new IOException("Error when closing zip process!", e);
			}
		}
		if(!this.closeables.isEmpty()) {
			this.closeables.clear();
		}
	}

	private TransferHandler getHandler(Map<Path, FileSystem> systems, Function<OutputTag, TransferHandler> handlerProvider, OutputTag output) {
		if(output == OutputTag.INPUT) {
			return TransferHandler.EMPTY;
		}
		// todo support directories
		TransferHandler system = output.path == null ? null : new TransferHandler.System(systems.computeIfAbsent(output.path, U::createZip), true);
		if(handlerProvider != null) {
			TransferHandler handler = handlerProvider.apply(output);
			if(handler == null) {
				return system;
			} else if(system == null) {
				return handler;
			} else {
				return handler.combine(system);
			}
		} else {
			return system;
		}
	}

	private List<OutputTag> getPaths() throws IOException {
		List<OutputTag> outputs = new ArrayList<>();
		for(ZipTagImpl tag : this.tags) {
			outputs.add(tag.getTag());
		}
		for(var zip : this.zips) {
			if(zip.b != null) {
				this.extracted(outputs, zip.zipPre.apply(zip.a), zip.a, zip.b, true);
			}
		}
		for(var process : this.processes) {
			for(OutputTag output : process.a.getOutputs()) {
				if(process.b != null) {
					this.extracted(outputs, process.zipPre.apply(output), output, process.b.apply(output), false);
				}
			}
		}
		outputs.addAll(this.processed);
		outputs.removeIf(Objects::isNull);
		return outputs;
	}

	private void extracted(List<OutputTag> outputs, ZipFilter processor, OutputTag input, OutputTag output, boolean tempSystem) throws IOException {
		Lazy<FileSystem> system = tempSystem ? new Lazy<>(() -> U.openZip(input.path)) : Lazy.empty();
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
			Function<OutputTag, TransferHandler> handlerProvider,
			Map<Path, FileSystem> toClose,
			List<VirtualZipEntry> afterAll,
			OutputTag input,
			OutputTag output,
			ZipFilter filter,
			PostZipProcessor transform,
			List<ToPostProcessPair> pairs) {
		Lazy<FileSystem> inputSystem = new Lazy<>(() -> toClose.computeIfAbsent(input.path, U::openZip));
		ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, inputSystem);
		if(behavior == ZipBehavior.CONTINUE) {
			TransferHandler transfer = this.getHandler(toClose, handlerProvider, output);
			if(transfer != null) {
				try(ProcessingTransferHandler processing = new ProcessingTransferHandler(transfer,
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

			if(output.path != null) {
				this.processed = U.add(this.processed, output);
			}
		} else if(behavior == ZipBehavior.COPY) {
			this.copy(handlerProvider, toClose, input);
		}
	}

	private void copy(Function<OutputTag, TransferHandler> handlerProvider, Map<Path, FileSystem> toClose, OutputTag input) {
		TransferHandler temp = handlerProvider == null ? null : handlerProvider.apply(input);
		TransferHandler transfer = this.getHandler(toClose, p -> temp, OutputTag.EMPTY);
		if(transfer != null) {
			FileSystem system = toClose.computeIfAbsent(input.path, U::openZip);
			try {
				visit(system, transfer);
			} catch(IOException e) {
				throw U.rethrow(e);
			}
		}
		this.processed = U.add(this.processed, input);
	}

	record ToPostProcessPair(TransferHandler handler, PostZipProcessor processor) {}

	static class Process<A, B> implements TaskTransform, ZipTransform {
		final A a;
		final B b;
		Function<OutputTag, ZipFilter> zipPre = p -> ZipFilter.DEFAULT;
		Function<OutputTag, PostZipProcessor> zipPost = p -> null;
		Function<OutputTag, ZipEntryProcessor> entryPost = p -> null, entryPre = p -> null;
		Predicate<OutputTag> pass = p -> false;

		Process(A process, B newOutput) {
			this.a = process;
			this.b = newOutput;
		}

		public void loadDefaults(Process<?, ?> process) {
			this.zipPre = process.zipPre;
			this.zipPost = process.zipPost;
			this.entryPre = process.entryPre;
			this.entryPost = process.entryPost;
			this.pass = process.pass;
		}

		@Override
		public void setZipFilter(Function<OutputTag, ZipFilter> processor) {
			this.zipPre = processor;
		}

		@Override
		public void setPostZipProcessor(Function<OutputTag, PostZipProcessor> processor) {
			this.zipPost = processor;
		}

		@Override
		public void setPostEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor) {
			this.entryPost = processor;
		}

		@Override
		public void setPreEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor) {
			this.entryPre = processor;
		}

		@Override
		public void setProcessOnly(Predicate<OutputTag> processOnly) {
			this.pass = processOnly;
		}
	}
}
