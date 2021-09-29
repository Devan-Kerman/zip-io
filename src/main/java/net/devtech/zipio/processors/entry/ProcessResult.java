package net.devtech.zipio.processors.entry;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.stage.ZipTransform;

public enum ProcessResult {
	/**
	 * refers the current entry to post-processing, returning this will cause the next ZipEntryProcessor stage to be called with this entry
	 * @see ZipProcessBuilder#setEntryProcessor(ZipEntryProcessor)
	 * @see ZipTransform#setPostEntryProcessor(ZipEntryProcessor)
	 * @see ZipProcessBuilder#setPostProcessor(ZipEntryProcessor)
	 * @see ZipEntryProcessor#apply(VirtualZipEntry)
	 */
	POST,
	// todo all input file systems need to be closed, or part of a seperate toClose map thing
	// cus if the input system is created but then a create system is needed, then we get crabbed
	// well actually, an input system can't be created if nothing exists there
	/**
	 * States the entry was handled, and thus full processing of the entry does not need to be postponed
	 */
	HANDLED;

	public static ProcessResult result(boolean shouldPost) {
		return shouldPost ? POST : HANDLED;
	}
}
