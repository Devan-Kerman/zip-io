package net.devtech.zipio.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import net.devtech.zipio.LinkedProcess;
import net.devtech.zipio.ZipEntryProcessor;
import net.devtech.zipio.ZipProcess;
import net.devtech.zipio.ZipProcessable;

public class EmptyZipProcessImpl implements ZipProcess {
	public static final EmptyZipProcessImpl INSTANCE = new EmptyZipProcessImpl();

	protected EmptyZipProcessImpl() {}

	@Override
	public LinkedProcess linkProcess(ZipProcess process, UnaryOperator<Path> newOutput) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ZipProcessable addZip(Path path, Path output) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addProcessed(Path output) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setEntryProcessor(ZipEntryProcessor processor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setPostProcessor(ZipEntryProcessor handler) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void afterExecute(Runnable runnable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void execute() {

	}

	@Override
	public Iterable<Path> getOutputs() {
		return List.of();
	}
}
