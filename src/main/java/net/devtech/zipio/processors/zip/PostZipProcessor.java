package net.devtech.zipio.processors.zip;

import net.devtech.zipio.ZipOutput;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;

/**
 * Generate extra resources in a zip after per-zip processing.
 */
public interface PostZipProcessor {
	/**
	 * called after {@link ZipProcessBuilder#setPostProcessor(ZipEntryProcessor)}
	 */
	void apply(ZipOutput output);
}
