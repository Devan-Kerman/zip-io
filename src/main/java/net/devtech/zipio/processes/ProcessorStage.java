package net.devtech.zipio.processes;

public enum ProcessorStage {
	/**
	 * First stage, run for every entry as it enters
	 */
	PRE_PER_ENTRY,
	/**
	 * "Second" stage, run for every post-entry after {@link #PRE_PER_ENTRY} for every entry as it enters
	 */
	PER_ENTRY,
	/**
	 * Third stage, run for every post-entry after *every* entry in a given input is processed by {@link #PER_ENTRY} & {@link #PRE_PER_ENTRY}
	 * (Basically it stores a list of all the post-entries, and after all of them are pre-processed, it runs through these)
	 */
	POST_INPUT,
	/**
	 * Fourth stage, run for every post-entry after *every* entry in a given <b>process</b> is processed by the previous stages
	 */
	POST_PROCESS
}
