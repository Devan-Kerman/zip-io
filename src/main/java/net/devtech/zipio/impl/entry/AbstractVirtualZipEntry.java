package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.nio.ByteBuffer;

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
		try {
			this.handler.write(buffer, fileName);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void writeToOutput(ByteBuffer buffer) {
		try {
			this.handler.write(buffer, this.destination);
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}
}
