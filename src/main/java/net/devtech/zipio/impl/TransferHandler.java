package net.devtech.zipio.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import net.devtech.zipio.ZipOutput;
import net.devtech.zipio.impl.util.U;

public interface TransferHandler extends AutoCloseable, ZipOutput {
	TransferHandler EMPTY = new TransferHandler() {
		@Override
		public void copy(String destination, Path path) {
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
		}

		@Override
		public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
		}

		@Override
		public void close() throws Exception {
		}
	};

	@Override
	void copy(String destination, Path path);

	@Override
	void write(String destination, ByteBuffer buffer);

	void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData);

	default TransferHandler combine(TransferHandler b) {
		if(this instanceof System s) { // prioritize writing to improve speed
			return new SystemOptimizedAndThen(s, b);
		} else if(b instanceof System s) {
			return new SystemOptimizedAndThen(s, this);
		} else {
			return new AndThen(this, b);
		}
	}

	record SystemOptimizedAndThen(System a, TransferHandler b) implements TransferHandler {
		@Override
		public void copy(String destination, Path path) {
			this.a.copy(destination, path);
			this.b.copy(destination, path);
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			Path compressed = this.a.write_(destination, buffer);
			this.b.copyWrite(destination, compressed, buffer);
		}

		@Override
		public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
			this.a.copyWrite(destination, compressedData, uncompressedData);
			this.b.copyWrite(destination, compressedData, uncompressedData);
		}

		@Override
		public void close() throws Exception {
			this.a.close();
			this.b.close();
		}
	}

	record AndThen(TransferHandler a, TransferHandler b) implements TransferHandler {
		@Override
		public void copy(String destination, Path path) {
			this.a.copy(destination, path);
			this.b.copy(destination, path);
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			this.a.write(destination, buffer);
			this.b.write(destination, buffer);
		}

		@Override
		public void close() throws Exception {
			this.a.close();
			this.b.close();
		}

		@Override
		public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
			this.a.copyWrite(destination, compressedData, uncompressedData);
			this.b.copyWrite(destination, compressedData, uncompressedData);
		}
	}

	record System(FileSystem system, boolean compressed) implements TransferHandler {
		@Override
		public void copy(String destination, Path path) {
			this.copy_(destination, path);
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			this.write_(destination, buffer);
		}

		@Override
		public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
			this.copy(destination, compressedData);
		}

		public Path write_(String destination, ByteBuffer buffer) {
			Path out;
			try(OutputStream stream = Files.newOutputStream(out = this.getOut(destination))) {
				stream.write(buffer.array(), buffer.arrayOffset(), buffer.limit());
			} catch(IOException e) {
				throw U.rethrow(e);
			}
			return out;
		}

		public Path copy_(String destination, Path path) {
			try {
				Path out = this.getOut(destination);
				Files.copy(path, out);
				return out;
			} catch(IOException e) {
				throw U.rethrow(e);
			}
		}

		@Override
		public void close() {
			// ignore
		}

		Path getOut(String destination) throws IOException {
			Path dest = this.system.getPath(destination);
			Path parent = dest.getParent();
			if(parent != null) {
				Files.createDirectories(parent);
			}
			return dest;
		}
	}
}
