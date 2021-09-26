package net.devtech.zipio.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import net.devtech.zipio.ZipEntryProcessor;
import net.devtech.zipio.ZipProcess;
import net.devtech.zipio.ZipProcessable;
import net.devtech.zipio.impl.util.U;

public final class FinishedZipProcessImpl extends EmptyZipProcessImpl implements ZipProcess {
	private final List<Path> paths;
	List<Runnable> runnables = List.of();

	public FinishedZipProcessImpl(List<Path> paths) {
		this.paths = paths;
	}

	@Override
	public void addProcessed(Path output) {
		this.paths.add(output);
	}

	@Override
	public void afterExecute(Runnable runnable) {
		this.runnables = U.add(this.runnables, runnable);
	}

	@Override
	public void execute() {
		for(Runnable runnable : this.runnables) {
			runnable.run();
		}
	}

	@Override
	public Iterable<Path> getOutputs() {
		return this.paths;
	}
}
