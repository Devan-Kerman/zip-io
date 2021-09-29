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
	@Override
	void copy(String destination, Path path);

	@Override
	void write(String destination, ByteBuffer buffer);

	default TransferHandler andThen(TransferHandler b) {
		return new AndThen(this, b);
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
	}

	class System implements TransferHandler {
		final FileSystem system;

		public System(FileSystem system) {
			this.system = system;
		}

		@Override
		public void copy(String destination, Path path) {
			try {
				Files.copy(path, this.getOut(destination));
			} catch(IOException e) {
				throw U.rethrow(e);
			}
		}

		@Override
		public void write(String destination, ByteBuffer buffer) {
			try(OutputStream stream = Files.newOutputStream(this.getOut(destination))) {
				stream.write(buffer.array(), buffer.arrayOffset(), buffer.limit());
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
