package net.devtech.zipio.processes;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.stage.ZipOutput;

public interface ZipProcessBuilder extends ZipProcess {
	/**
	 * Maps the output of the given zip process to a new set of outputs, the given process will be executed on this execute
	 */
	ZipOutput linkProcess(ZipProcess process, UnaryOperator<Path> newOutput);

	ZipOutput addZip(Path path, Path output);

	/**
	 * @param output Adds a zip to {@link #getOutputs()}
	 */
	void addProcessed(Path output);

	/**
	 * Sets the entry processor, this processor is executed once for every entry in the ZipProcess
	 * if {@link ProcessResult#POST} is returned here, then the ZipProcessor and/or PostProcessor will be invoked
	 *  with the given entry at the relevant stage
	 * @see ZipOutput
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
}
