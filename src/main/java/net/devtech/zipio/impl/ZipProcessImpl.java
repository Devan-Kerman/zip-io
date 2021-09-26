package net.devtech.zipio.impl;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.ProcessResult;
import net.devtech.zipio.ZipEntryProcessor;
import net.devtech.zipio.ZipProcess;
import net.devtech.zipio.ZipProcessable;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.impl.util.BufferPool;
import net.devtech.zipio.impl.util.U;

public class ZipProcessImpl implements ZipProcess {
	List<Linked<ZipProcessImpl, UnaryOperator<Path>>> processes = List.of();
	List<Linked<Path, Path>> zips = List.of();
	ZipEntryProcessor entry, post;

	@Override
	public ZipProcessable linkProcess(ZipProcess process, UnaryOperator<Path> newOutput) {
		var linked = new Linked<>((ZipProcessImpl) process, newOutput);
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
	public void setEntryProcessor(ZipEntryProcessor processor) {
		this.entry = processor;
	}

	@Override
	public void setPostProcessor(ZipEntryProcessor handler) {
		this.post = handler;
	}

	@Override
	public void execute() throws IOException {
		this.execute(null);
	}

	/**
	 * This uses the unary operator to get a FileSystem transfer handler
	 */
	void execute(Function<Path, TransferHandler> handlerProvider) throws IOException {
		List<FileSystem> toClose = new ArrayList<>();
		List<VirtualZipEntry> afterAll = this.post == null ? null : new Vector<>();
		for(Linked<Path, Path> zip : this.zips) {
			Files.deleteIfExists(zip.b);
			FileSystem in = FileSystems.newFileSystem(zip.a);
			FileSystem out = U.createZip(zip.b);
			TransferHandler transfer = this.getHandler(handlerProvider, zip.b, out);
			this.accept(afterAll, transfer, zip.processor, t -> {
				for(Path directory : in.getRootDirectories()) {
					Files.walk(directory).parallel().filter(Files::isRegularFile).forEach(path -> {
						String to = directory.relativize(path).toString();
						try {
							t.copyFrom(path, to);
						} catch(IOException e) {
							e.printStackTrace();
						}
					});
				}
			}, hasPost -> {
				if(hasPost) {
					toClose.add(in);
					toClose.add(out);
				} else {
					in.close();
					out.close();
				}
			});
		}

		for(Linked<ZipProcessImpl, UnaryOperator<Path>> process : this.processes) {
			process.a.execute(p -> {
				Path output = process.b.apply(p);
				try {
					Files.deleteIfExists(output);
				} catch(IOException e) {
					throw U.rethrow(e);
				}
				FileSystem out = U.createZip(output);
				TransferHandler transfer = this.getHandler(handlerProvider, output, out);
				return new ProcessingTransferHandler(transfer, afterAll, this.entry, process.processor) {
					@Override
					public void close() throws Exception {
						super.close();
						out.close();
					}
				};
			});
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

		for(FileSystem system : toClose) {
			system.close();
		}
	}

	private TransferHandler getHandler(Function<Path, TransferHandler> handlerProvider, Path output, FileSystem out) {
		TransferHandler system = new TransferHandler.System(out);
		if(handlerProvider != null) {
			TransferHandler handler = handlerProvider.apply(output);
			return handler.andThen(system);
		} else {
			return system;
		}
	}

	interface UConsumer<T> {
		void accept(T val) throws IOException;
	}

	void accept(List<VirtualZipEntry> afterAll, TransferHandler inputHandler, ZipEntryProcessor zip, UConsumer<TransferHandler> acceptor, UConsumer<Boolean> has)
			throws IOException {
		boolean hasPost = false;
		try(ProcessingTransferHandler processing = new ProcessingTransferHandler(inputHandler, afterAll, this.entry, zip)) {
			acceptor.accept(processing);
			hasPost = processing.hasPost;
		} catch(Exception e) {
			throw U.rethrow(e);
		} finally {
			has.accept(hasPost);
		}
	}

	static final class Linked<A, B> implements ZipProcessable {
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
}
