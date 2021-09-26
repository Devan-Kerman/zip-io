package net.devtech.zipio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import net.devtech.zipio.impl.EmptyZipProcessImpl;
import net.devtech.zipio.impl.FinishedZipProcessImpl;
import net.devtech.zipio.impl.ZipProcessImpl;

public interface ZipProcess {
	static ZipProcess create() {
		return new ZipProcessImpl();
	}

	static ZipProcess processed(Path... paths) {
		return new FinishedZipProcessImpl(Arrays.asList(paths));
	}

	static ZipProcess processed(List<Path> paths) {
		return new FinishedZipProcessImpl(new ArrayList<>(paths));
	}

	static ZipProcess empty() {
		return EmptyZipProcessImpl.INSTANCE;
	}

	/**
	 * Maps the output of the given zip process to a new set of outputs, the given process will be executed on this execute
	 */
	LinkedProcess linkProcess(ZipProcess process, UnaryOperator<Path> newOutput);

	ZipProcessable addZip(Path path, Path output);

	/**
	 * @param output Adds a zip to {@link #getOutputs()}
	 */
	void addProcessed(Path output);

	/**
	 * Sets the entry processor, this processor is executed once for every entry in the ZipProcess
	 * if {@link ProcessResult#POST} is returned here, then the ZipProcessor and/or PostProcessor will be invoked
	 *  with the given entry at the relevant stage
	 * @see ZipProcessable
	 */
	void setEntryProcessor(ZipEntryProcessor processor);

	/**
	 * Sets the post processor, this processor is executed once for every entry after all ZipProcessors are invoked. (or all of them if there is no zip/entry processor)
	 *  if {@link ProcessResult#POST} is returned here, then the PostProcessor will be invoked with the given entry
	 */
	void setPostProcessor(ZipEntryProcessor handler);

	/**
	 * Adds a listener that is fired after this process is executed
	 */
	void afterExecute(Runnable runnable);

	void execute() throws IOException;

	/**
	 * @return the current outputs of the ZipProcess, files may or may not exist when this method is invoked.
	 *  After {@link #execute()} is called, they *should* exist.
	 */
	Iterable<Path> getOutputs();
}
