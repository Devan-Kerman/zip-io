package net.devtech.zipio.processors.entry;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.processors.entry.ProcessResult;

public interface ZipEntryProcessor {
	/**
	 * Process the given zip entry.
	 *  For example, if you want to do nothing, aka copy the entry from input to output use {@link VirtualZipEntry#copyToOutput()}
	 * @see ProcessResult#POST
	 * @see ProcessResult#HANDLED
	 */
	ProcessResult apply(VirtualZipEntry buffer);
}
