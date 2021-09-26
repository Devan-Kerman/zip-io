package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.util.U;

public class InMemoryZipEntryImpl extends AbstractVirtualZipEntry {
	public final ByteBuffer contents;

	public InMemoryZipEntryImpl(TransferHandler handler, ByteBuffer contents, String destination) {
		super(handler, destination);
		this.contents = contents;
	}

	@Override
	public ByteBuffer read() {
		return this.contents;
	}

	@Override
	public void copyToOutput() {
		try {
			this.handler.write(this.contents, this.destination);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void copyTo(String fileName) {
		try {
			this.handler.write(this.contents, fileName);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void copyTo(Path path) {
		try(OutputStream out = Files.newOutputStream(path)) {
			out.write(this.contents.array(), this.contents.arrayOffset(), this.contents.position());
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}
}
