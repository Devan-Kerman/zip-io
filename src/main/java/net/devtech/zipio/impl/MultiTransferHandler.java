package net.devtech.zipio.impl;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

public record MultiTransferHandler(List<TransferHandler> handlers) implements TransferHandler {

	@Override
	public void copy(String destination, Path path) {
		for(TransferHandler handler : handlers) {
			handler.copy(destination, path);
		}
	}

	@Override
	public void write(String destination, ByteBuffer buffer) {
		for(TransferHandler handler : handlers) {
			handler.write(destination, buffer);
		}
	}

	@Override
	public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
		for(TransferHandler handler : handlers) {
			handler.copyWrite(destination, compressedData, uncompressedData);
		}
	}

	@Override
	public void close() throws Exception {
		for(TransferHandler handler : handlers) {
			handler.close();
		}
	}
}
