package net.devtech.zipio.impl;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import net.devtech.zipio.LinkedProcess;
import net.devtech.zipio.ProcessResult;
import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.ZipEntryProcessor;
import net.devtech.zipio.ZipProcess;
import net.devtech.zipio.ZipProcessable;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.impl.util.BufferPool;
import net.devtech.zipio.impl.util.U;

public class ZipProcessImpl implements ZipProcess {
	List<Path> processed;
	List<Runnable> afterExecute = List.of();
	List<LinkedProcessImpl> processes = List.of();
	List<Linked<Path, Path>> zips = List.of();
	ZipEntryProcessor entry, post;

	@Override
	public LinkedProcess linkProcess(ZipProcess process, UnaryOperator<Path> newOutput) {
		var linked = new LinkedProcessImpl(process, newOutput);
		this.processes = U.add(this.processes, linked);
		return linked;
	}

	@Override
	public ZipProcessable addZip(Path path, Path output) {
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
		this.entry = processor;
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
		this.execute(null);
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

	/**
	 * This uses the unary operator to get a FileSystem transfer handler
	 */
	void execute(Function<Path, TransferHandler> handlerProvider) throws IOException {
		Map<Path, FileSystem> toClose = new HashMap<>();
		List<VirtualZipEntry> afterAll = this.post == null ? null : new Vector<>();
		for(Linked<Path, Path> zip : this.zips) {
			Path input = zip.a, output = zip.b;
			ZipEntryProcessor processor = zip.processor;
			this.invokeFileIO(handlerProvider, toClose, afterAll, input, output, processor);
		}

		for(LinkedProcessImpl process : this.processes) {
			Predicate<Path> predicate = process.predicate;
			if(process.a instanceof ZipProcessImpl i) {
				i.execute(p -> {
					if(!predicate.test(p)) {
						return null;
					}
					// and here
					Path output = process.b.apply(p);
					FileSystem out = toClose.computeIfAbsent(output, U::createZip);
					TransferHandler transfer = this.getHandler(handlerProvider, output, out); // todo handle null for handler provider
					return new ProcessingTransferHandler(transfer, afterAll, this.entry, process.processor) {
						@Override
						public void close() throws Exception {
							super.close();
							if(out != null && !this.hasPost) {
								toClose.remove(output);
								out.close();
							}
						}
					};
				});
				for(Path p : i.processed) {
					if(!predicate.test(p)) {
						continue;
					}
					this.invokeFileIO(handlerProvider, toClose, afterAll, p, process.b.apply(p), process.processor);
				}
			} else {
				process.a.execute();
				for(Path output : process.a.getOutputs()) {
					if(!predicate.test(output)) {
						continue;
					}
					// filter needs to be placed here
					this.invokeFileIO(handlerProvider, toClose, afterAll, output, process.b.apply(output), process.processor);
				}
			}
		}

		if(afterAll != null) {
			for(VirtualZipEntry impl : afterAll) {
				ProcessResult result = this.post.apply(impl);
				if(result == ProcessResult.POST) {
					throw new IllegalStateException(this.entry + " return POST in post handler! (final stage, can't post past final)");
				} else if(impl instanceof RealZipEntry i && i.lazy.supplier != null) {
					BufferPool.INSTANCE.ret(i.lazy.value, 0);
				}
			}
		}

		for(FileSystem system : toClose.values()) {
			system.close();
		}

		this.afterExecute.forEach(Runnable::run);
	}

	private void invokeFileIO(Function<Path, TransferHandler> handlerProvider,
			Map<Path, FileSystem> toClose,
			List<VirtualZipEntry> afterAll,
			Path input,
			Path output,
			ZipEntryProcessor processor) throws IOException {
		FileSystem in = input == null ? null : FileSystems.newFileSystem(output);
		FileSystem out = toClose.computeIfAbsent(output, U::createZip);
		TransferHandler transfer = this.getHandler(handlerProvider, output, out);
		boolean hasPost = false;
		try(ProcessingTransferHandler processing = new ProcessingTransferHandler(transfer, afterAll, this.entry, processor)) {
			for(Path directory : in.getRootDirectories()) {
				Files.walk(directory).parallel().filter(Files::isRegularFile).forEach(path -> {
					String to = directory.relativize(path).toString();
					try {
						processing.copyFrom(path, to);
					} catch(IOException e) {
						e.printStackTrace();
					}
				});
			}
			hasPost = processing.hasPost;
		} catch(Exception e1) {
			throw U.rethrow(e1);
		} finally {
			if(!hasPost) {
				if(in != null) {
					toClose.remove(input);
					in.close();
				}
				if(out != null) {
					toClose.remove(output);
					out.close();
				}
			}
		}
	}

	private TransferHandler getHandler(Function<Path, TransferHandler> handlerProvider, Path output, FileSystem out) {
		TransferHandler system = new TransferHandler.System(out);
		if(handlerProvider != null) {
			TransferHandler handler = handlerProvider.apply(output);
			return handler == null ? system : handler.andThen(system);
		} else {
			return system;
		}
	}

	static class Linked<A, B> implements ZipProcessable {
		final A a;
		final B b;
		ZipEntryProcessor processor;

		Linked(A process, B newOutput) {
			this.a = process;
			this.b = newOutput;
		}

		@Override
		public void setZipProcessor(ZipEntryProcessor processor) {
			this.processor = processor;
		}
	}

	static class LinkedProcessImpl extends Linked<ZipProcess, UnaryOperator<Path>> implements LinkedProcess {
		Predicate<Path> predicate = p -> true;

		LinkedProcessImpl(ZipProcess process, UnaryOperator<Path> newOutput) {
			super(process, newOutput);
		}

		@Override
		public void setFilter(Predicate<Path> path) {
			this.predicate = path;
		}
	}
}
