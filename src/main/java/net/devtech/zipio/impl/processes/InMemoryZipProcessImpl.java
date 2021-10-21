package net.devtech.zipio.impl.processes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.impl.entry.CopyWriteZipEntryImpl;
import net.devtech.zipio.impl.entry.InMemoryZipEntryImpl;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.processes.MemoryZipProcess;
import net.devtech.zipio.impl.InternalZipProcess;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.util.U;

public class InMemoryZipProcessImpl implements MemoryZipProcess, InternalZipProcess, Function<OutputTag, TransferHandler> {
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
	public Iterable<OutputTag> getOutputs() {
		// todo
		return List.of();
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<OutputTag, TransferHandler> provider) throws IOException {
		for(TransferHandlerPair pair : this.pairs) {
			TransferHandler handler = provider.apply(pair.path);
			TransferHandlerNode node = pair.handler;
			for(TransferPair<Path> copy : node.copyPair) {
				handler.copy(copy.destination, copy.data);
			}
			for(TransferPair<ByteBuffer> write : node.writePair) {
				handler.write(write.destination, write.data);
			}
			for(TransferCopyPair copy : node.optCopies) {
				handler.copyWrite(copy.destination, copy.path, copy.buffer);
			}
		}
	}

	@Override
	public Iterable<OutputTag> processed() {
		return List.of();
	}

	@Override
	public void close() throws Exception {
		U.close(this.toClose);
	}

	@Override
	public TransferHandler apply(OutputTag tag) {
		TransferHandlerNode handler = new TransferHandlerNode();
		this.pairs = U.add(this.pairs, new TransferHandlerPair(tag, handler));
		return handler;
	}

	public static class TransferHandlerNode implements TransferHandler {
		List<TransferPair<Path>> copyPair = List.of();
		List<TransferPair<ByteBuffer>> writePair = List.of();
		List<TransferCopyPair> optCopies = List.of();

		@Override
		public void copy(String destination, Path path) {
			this.copyPair = U.add(this.copyPair, new TransferPair<>(path, destination));
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			this.writePair = U.add(this.writePair, new TransferPair<>(buffer, destination));
		}

		@Override
		public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
			this.optCopies = U.add(this.optCopies, new TransferCopyPair(compressedData, uncompressedData, destination));
		}

		@Override
		public void close() {

		}

		public static void add(TransferHandler handler, VirtualZipEntry entry) {
			if(entry instanceof CopyWriteZipEntryImpl w) {
				handler.copyWrite(w.destination, w.compressedPath, w.uncompressedData);
			} else if(entry instanceof RealZipEntry r) {
				handler.copy(r.destination, r.inputFile);
			} else if(entry instanceof InMemoryZipEntryImpl i) {
				handler.write(i.destination, i.contents);
			} else {
				throw new UnsupportedOperationException("Unknown type " + entry.getClass());
			}
		}

		public void appendToList(TransferHandler handler, Consumer<VirtualZipEntry> entries) {
			for(TransferPair<Path> pair : this.copyPair) {
				entries.accept(new RealZipEntry(handler, pair.data, pair.destination));
			}
			for(TransferPair<ByteBuffer> pair : this.writePair) {
				entries.accept(new InMemoryZipEntryImpl(handler, pair.data, pair.destination));
			}
			for(TransferCopyPair copy : this.optCopies) {
				entries.accept(new CopyWriteZipEntryImpl(handler, copy.destination, copy.path, copy.buffer));
			}
		}
	}

	record TransferPair<T>(T data, String destination) {}
	record TransferHandlerPair(OutputTag path, TransferHandlerNode handler) {}
	record TransferCopyPair(Path path, ByteBuffer buffer, String destination) {}
}
