package net.devtech.zipio.impl.processes;

import java.nio.file.Path;
import java.util.List;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.processes.MemoryZipProcess;

public class EmptyZipProcessImpl implements MemoryZipProcess {
	public static final EmptyZipProcessImpl INSTANCE = new EmptyZipProcessImpl();

	protected EmptyZipProcessImpl() {}

	@Override
	public MemoryZipProcess executeCached() {
		return this;
	}

	@Override
	public Iterable<OutputTag> getOutputs() {
		return List.of();
	}

	@Override
	public void close() throws Exception {

	}
}
