package net.devtech.zipio.impl;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

public interface InternalZipProcess {
	void execute(Map<Path, FileSystem> toClose, Function<Path, TransferHandler> handlerProvider) throws IOException;

	Iterable<Path> processed();
}
