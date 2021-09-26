package net.devtech.zipio.impl.processes;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.impl.InternalZipProcess;
import net.devtech.zipio.impl.ProcessingTransferHandler;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.impl.util.BufferPool;
import net.devtech.zipio.impl.util.Lazy;
import net.devtech.zipio.impl.util.U;
import net.devtech.zipio.processes.MemoryZipProcess;
import net.devtech.zipio.processes.ZipProcess;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.ZipBehavior;
import net.devtech.zipio.processors.zip.ZipProcessor;
import net.devtech.zipio.stage.ZipOutput;

public class ZipProcessImpl implements ZipProcessBuilder, InternalZipProcess {
	List<Path> processed = List.of();
	List<Runnable> afterExecute = List.of();
	List<Linked<ZipProcess, UnaryOperator<Path>>> processes = List.of();
	List<Linked<Path, Path>> zips = List.of();
	ZipEntryProcessor perEntry, post;

	@Override
	public ZipOutput linkProcess(ZipProcess process, UnaryOperator<Path> newOutput) {
		var linked = new Linked<>(process, newOutput);
		this.processes = U.add(this.processes, linked);
		return linked;
	}

	@Override
	public ZipOutput addZip(Path path, Path output) {
		var linked = new Linked<>(path, output);
		this.zips = U.add(this.zips, linked);
		return linked;
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
		this.post = handler;
	}

	@Override
	public void afterExecute(Runnable runnable) {
		this.afterExecute = U.add(this.afterExecute, runnable);
	}

	@Override
	public void execute() throws IOException {
		// todo in-memory representation of zip, goes here, should be enough I think
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
		List<Path> outputs = new ArrayList<>();
		for(Linked<Path, Path> zip : this.zips) {
			if(zip.b != null) {
				outputs.add(zip.b);
			}
		}
		for(Linked<ZipProcess, UnaryOperator<Path>> process : this.processes) {
			for(Path output : process.a.getOutputs()) {
				outputs.add(process.b.apply(output));
			}
		}
		return outputs;
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<Path, TransferHandler> handlerProvider) throws IOException {
		List<VirtualZipEntry> afterAll = this.post == null ? null : new Vector<>();
		for(Linked<Path, Path> zip : this.zips) {
			Path input = zip.a, output = zip.b;
			ZipEntryProcessor processor = zip.entry;
			this.invokeFileIO(handlerProvider, toClose, afterAll, input, output, processor, zip.zip);
		}

		for(Linked<ZipProcess, UnaryOperator<Path>> process : this.processes) {
			if(process.a instanceof InternalZipProcess i) {
				for(Path p : i.processed()) { // this must be first
					this.invokeFileIO(handlerProvider, toClose, afterAll, p, process.b.apply(p), process.entry, process.zip);
				}

				i.execute(toClose, p -> {
					// and here
					Path output = process.b.apply(p);
					ZipBehavior behavior = process.zip.test(output, Lazy.empty());
					if(behavior == ZipBehavior.CONTINUE) {
						TransferHandler transfer = getHandler(toClose, handlerProvider, output);
						if(output != null) {
							this.processed.add(output);
						}
						if(transfer != null) {
							return new ProcessingTransferHandler(transfer, afterAll, this.perEntry, process.entry);
						} else {
							return null;
						}
					} else if(behavior == ZipBehavior.SKIP) {
						this.processed.add(p);
						return null;
					} else {
						return null;
					}
				});
			} else {
				process.a.execute();
				for(Path output : process.a.getOutputs()) {
					// filter needs to be placed here
					this.invokeFileIO(handlerProvider, toClose, afterAll, output, process.b.apply(output), process.entry, process.zip);
				}
			}
		}

		if(afterAll != null) {
			for(VirtualZipEntry impl : afterAll) {
				ProcessResult result = this.post.apply(impl);
				if(result == ProcessResult.POST) {
					throw new IllegalStateException(this.perEntry + " return POST in post handler! (final stage, can't post past final)");
				} else if(impl instanceof RealZipEntry i && i.lazy.supplier != null) {
					BufferPool.INSTANCE.ret(i.lazy.value, 0);
				}
			}
		}

		this.zips.clear();
		this.processes.clear();

		this.afterExecute.forEach(Runnable::run);
	}

	@Override
	public Iterable<Path> processed() {
		return this.processed;
	}

	private static void visit(FileSystem in, TransferHandler handler) throws IOException {
		for(Path directory : in.getRootDirectories()) {
			Files.walk(directory).parallel().filter(Files::isRegularFile).forEach(path -> {
				String to = directory.relativize(path).toString();
				try {
					handler.copyFrom(path, to);
				} catch(IOException e) {
					throw U.rethrow(e);
				}
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

	private void invokeFileIO(Function<Path, TransferHandler> handlerProvider,
			Map<Path, FileSystem> toClose,
			List<VirtualZipEntry> afterAll,
			Path input,
			Path output,
			ZipEntryProcessor processor,
			ZipProcessor zip) {
		Lazy<FileSystem> inputSystem = new Lazy<>(() -> toClose.computeIfAbsent(input, U::openZip));
		ZipBehavior behavior = zip.test(input, inputSystem);
		if(behavior == ZipBehavior.CONTINUE) {
			TransferHandler transfer = getHandler(toClose, handlerProvider, output);
			if(transfer != null) {
				try(ProcessingTransferHandler processing = new ProcessingTransferHandler(transfer, afterAll, this.perEntry, processor)) {
					visit(inputSystem.get(), processing);
				} catch(Exception e1) {
					throw U.rethrow(e1);
				}
			}

			if(output != null) {
				this.processed.add(output);
			}
		} else if(behavior == ZipBehavior.SKIP) {
			this.processed.add(input);
		}
	}

	static class Linked<A, B> implements ZipOutput {
		final A a;
		final B b;
		ZipEntryProcessor entry;
		ZipProcessor zip;

		Linked(A process, B newOutput) {
			this.a = process;
			this.b = newOutput;
		}

		@Override
		public void setPostEntryProcessor(ZipEntryProcessor processor) {
			this.entry = processor;
		}

		@Override
		public void setZipProcessor(ZipProcessor processor) {
			this.zip = processor;
		}
	}
}
