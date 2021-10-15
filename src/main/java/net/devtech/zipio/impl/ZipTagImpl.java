package net.devtech.zipio.impl;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Function;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.ZipTag;

public class ZipTagImpl implements ZipTag {
	public final OutputTag tag;
	public TransferHandler handler;

	public ZipTagImpl(OutputTag tag) {
		this.tag = tag;
	}

	@Override
	public void write(String fileName, ByteBuffer buffer) {
		if(this.handler == null) {
			throw new IllegalStateException("Not executing!");
		}
		this.handler.write(fileName, buffer);
	}

	@Override
	public void copy(String fileName, Path input) {
		if(this.handler == null) {
			throw new IllegalStateException("Not executing!");
		}
		this.handler.copy(fileName, input);
	}

	public void copyWrite(String fileName, Path compressedData, ByteBuffer uncompressedData) {
		if(this.handler == null) {
			throw new IllegalStateException("Not executing!");
		}
		this.handler.copyWrite(fileName, compressedData, uncompressedData);
	}

	@Override
	public boolean isExecuting() {
		return this.handler != null;
	}

	@Override
	public OutputTag getTag() {
		return this.tag;
	}
}
