package net.devtech.zipio.processes;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.ZipTag;
import net.devtech.zipio.impl.ZipTagImpl;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.ZipFilter;
import net.devtech.zipio.stage.TaskTransform;
import net.devtech.zipio.stage.ZipTransform;

public interface ZipProcessBuilder extends ZipProcess {
	TaskTransform defaults();

	/**
	 * Adds a closable task to this builder, when the process is executed, this is the last listener that is called
	 */
	void addCloseable(Closeable closeable);

	/**
	 * Maps the output of the given zip process to a new set of outputs, the given process will be executed on this execute
	 */
	TaskTransform linkProcess(ZipProcess process, UnaryOperator<OutputTag> newOutput);

	ZipTransform addZip(Path path, OutputTag output);

	default ZipTransform addZip(Path path, Path output) {
		return this.addZip(path, new OutputTag(output));
	}

	ZipTag createZipTag(OutputTag output);

	default ZipTag createZipTag(Path output) {
		return this.createZipTag(new OutputTag(output));
	}

	/**
	 * @param output Adds a zip to {@link #getOutputs()}
	 */
	void addProcessed(OutputTag output);

	default void addProcessed(Path output) {
		this.addProcessed(new OutputTag(output));
	}

	/**
	 * Sets the entry processor, this processor is executed once for every entry in the ZipProcess
	 * if {@link ProcessResult#POST} is returned here, then the ZipProcessor and/or PostProcessor will be invoked
	 *  with the given entry at the relevant stage
	 * @see ZipTransform
	 */
	void setEntryProcessor(ZipEntryProcessor processor);

	/**
	 * Sets the post processor, this processor is executed once for every entry after all ZipProcessors are invoked. (or all of them if there is no zip/entry processor)
	 *  if {@link ProcessResult#POST} is returned here, then the PostProcessor will be invoked with the given entry
	 */
	void setPostProcessor(ZipEntryProcessor handler);

	/**
	 * Adds a listener that is fired after this process is executed. The ZipProcess is still executing in this stage, so ZipTags can be written to here
	 */
	void afterExecute(Runnable runnable);
}
