package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import net.devtech.zipio.impl.util.Lazy;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.util.U;

public class RealZipEntry extends AbstractVirtualZipEntry {
	public final Lazy<ByteBuffer> lazy;
	public final Path inputFile;

	public RealZipEntry(TransferHandler handler, Path inputFile, String destination) {
		super(handler, destination);
		this.lazy = new Lazy<>(() -> U.read(inputFile));
		this.inputFile = inputFile;
	}

	@Override
	public ByteBuffer read() {
		return this.lazy.get();
	}

	@Override
	public void copyToOutput() {
		try {
			this.handler.copyFrom(this.inputFile, this.destination);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void copyTo(String fileName) {
		try {
			this.handler.copyFrom(this.inputFile, fileName);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void copyTo(Path path) {
		try {
			Files.copy(this.inputFile, path);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}
}
