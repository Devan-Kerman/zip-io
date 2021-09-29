package net.devtech.zipio.impl.processes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.devtech.zipio.processes.MemoryZipProcess;
import net.devtech.zipio.impl.InternalZipProcess;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.util.U;

public class InMemoryZipProcessImpl implements MemoryZipProcess, InternalZipProcess, Function<Path, TransferHandler> {
	final Map<Path, FileSystem> toClose;
	List<TransferHandlerPair> pairs = List.of();

	public InMemoryZipProcessImpl(Map<Path, FileSystem> close) {
		this.toClose = close;
	}

	@Override
	public void execute() {
	}

	@Override
	public MemoryZipProcess executeCached() {
		return this;
	}

	@Override
	public Iterable<Path> getOutputs() {
		return null;
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<Path, TransferHandler> provider) throws IOException {
		for(TransferHandlerPair pair : this.pairs) {
			TransferHandler handler = provider.apply(pair.path);
			TransferHandlerNode node = pair.handler;
			for(TransferPair<Path> copy : node.copyPair) {
				handler.copy(copy.destination, copy.data);
			}
			for(TransferPair<ByteBuffer> write : node.writePair) {
				handler.write(write.destination, write.data);
			}
		}
	}

	@Override
	public Iterable<Path> processed() {
		return List.of();
	}

	@Override
	public TransferHandler apply(Path path) {
		TransferHandlerNode handler = new TransferHandlerNode();
		this.pairs = U.add(this.pairs, new TransferHandlerPair(path, handler));
		return handler;
	}

	@Override
	public void close() throws Exception {
		U.close(this.toClose);
	}

	public static class TransferHandlerNode implements TransferHandler {
		List<TransferPair<Path>> copyPair = List.of();
		List<TransferPair<ByteBuffer>> writePair = List.of();

		@Override
		public void copy(String destination, Path path) {
			this.copyPair = U.add(this.copyPair, new TransferPair<>(path, destination));
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			// buffers are pooled and temporary, so we must copy them
			int pos = buffer.limit();
			ByteBuffer clone = ByteBuffer.allocate(pos);
			clone.put(0, buffer, 0, pos);
			clone.limit(pos);
			this.writePair = U.add(this.writePair, new TransferPair<>(clone, destination));
		}

		@Override
		public void close() {

		}
	}

	record TransferPair<T>(T data, String destination) {}
	record TransferHandlerPair(Path path, TransferHandlerNode handler) {}
}
