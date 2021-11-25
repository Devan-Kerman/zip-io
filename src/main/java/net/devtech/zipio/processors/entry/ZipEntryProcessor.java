package net.devtech.zipio.processors.entry;

import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.processes.ProcessorStage;

public interface ZipEntryProcessor {
	/**
	 * Process the given zip entry.
	 *  For example, if you want to do nothing, aka copy the entry from input to output use {@link VirtualZipEntry#copyToOutput()}
	 * @see ProcessResult#POST
	 * @see ProcessResult#HANDLED
	 */
	ProcessResult apply(VirtualZipEntry buffer);

	/**
	 * todo we can optimize this by pre-computing the list, atm this optimization isn't used by amalg so it's low priority
	 * @param currentStage the stage the processor is currently registered for
	 * @param incomingStage the stage the extra output is being pulled from (will always be a prior or equal stage)
	 * @return whether extra outputs (eg. non-VirtualZipEntry.XToOutput) from the 'previous' processor within the same process should be processed by this processor
	 *  if this returns false, they are "copied" to the next stage
	 */
	default boolean acceptsExtraOutputs(ProcessorStage currentStage, ProcessorStage incomingStage) {
		// by default, don't process extra outputs from the same stage, this is to avoid dependency on load order
		return currentStage.ordinal() > incomingStage.ordinal();
	}
}
