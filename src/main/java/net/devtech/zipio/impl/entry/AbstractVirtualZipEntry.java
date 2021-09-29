package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.util.U;

public abstract class AbstractVirtualZipEntry implements VirtualZipEntry {
	public final TransferHandler handler;
	public final String destination;

	protected AbstractVirtualZipEntry(TransferHandler handler, String destination) {
		this.handler = handler;
		this.destination = destination;
	}

	@Override
	public String path() {
		return this.destination;
	}

	@Override
	public void write(String fileName, ByteBuffer buffer) {
		this.handler.write(fileName, buffer);
	}

	@Override
	public void writeToOutput(ByteBuffer buffer) {
		this.handler.write(this.destination, buffer);
	}

	@Override
	public void copy(String fileName, Path input) {
		this.handler.copy(fileName, input);
	}
}
