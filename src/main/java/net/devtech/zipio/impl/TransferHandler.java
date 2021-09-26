package net.devtech.zipio.impl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public interface TransferHandler extends AutoCloseable {
	void copyFrom(Path path, String destination) throws IOException;

	void write(ByteBuffer buffer, String destination) throws IOException;

	default TransferHandler andThen(TransferHandler b) {
		return new AndThen(this, b);
	}

	record AndThen(TransferHandler a, TransferHandler b) implements TransferHandler {
		@Override
		public void copyFrom(Path path, String destination) throws IOException {
			this.a.copyFrom(path, destination);
			this.b.copyFrom(path, destination);
		}

		@Override
		public void write(ByteBuffer buffer, String destination) throws IOException {
			this.a.write(buffer, destination);
			this.b.write(buffer, destination);
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
		public void copyFrom(Path path, String destination) throws IOException {
			Files.copy(path, this.getOut(destination));
		}

		@Override
		public void write(ByteBuffer buffer, String destination) throws IOException {
			try(OutputStream stream = Files.newOutputStream(this.getOut(destination))) {
				stream.write(buffer.array(), buffer.arrayOffset(), buffer.position());
			}
		}

		@Override
		public void close() throws Exception {
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
