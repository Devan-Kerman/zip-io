package net.devtech.zipio.stage;

import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.ZipProcessor;

public interface ZipOutput {
	/**
	 * @param processor This processor is fired for every entry with {@link ProcessResult#POST} (or for every entry if there is no entry processor) in this object
	 */
	void setPostEntryProcessor(ZipEntryProcessor processor);

	void setZipProcessor(ZipProcessor processor);

	// todo zip skipper: something here that is passed the input file system (or just the path + lazy fs) and if it skips then add the input to output
}
