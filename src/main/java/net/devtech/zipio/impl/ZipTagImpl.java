package net.devtech.zipio.impl;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import net.devtech.zipio.ZipTag;

public class ZipTagImpl implements ZipTag {
	public final Path output;
	public TransferHandler handler;

	public ZipTagImpl(Path output) {this.output = output;}

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

	@Override
	public boolean isExecuting() {
		return this.handler != null;
	}
}
