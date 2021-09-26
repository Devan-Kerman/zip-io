package net.devtech.zipio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public interface ZipProcess {
	/**
	 * Maps the output of the given zip process to a new set of outputs, the given process will be executed on this execute
	 */
	ZipProcessable linkProcess(ZipProcess process, UnaryOperator<Path> newOutput);

	ZipProcessable addZip(Path path, Path output);

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

	void execute() throws IOException;

}
