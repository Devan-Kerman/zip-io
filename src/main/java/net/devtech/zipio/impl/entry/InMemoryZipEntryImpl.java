package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import net.devtech.zipio.ZipTag;
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
		this.contents.rewind();
		return this.contents;
	}

	@Override
	public void copyToOutput() {
		this.handler.write(this.destination, this.contents);
	}

	@Override
	public void copyTo(String fileName) {
		this.handler.write(fileName, this.contents);
	}

	@Override
	public void copyTo(Path path) {
		try(OutputStream out = Files.newOutputStream(path)) {
			out.write(this.contents.array(), this.contents.arrayOffset(), this.contents.limit());
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void copyTo(String path, ZipTag tag) {
		tag.write(path, this.contents);
	}
}
