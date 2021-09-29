package net.devtech.zipio.processors.zip;

public enum ZipBehavior {
	/**
	 * Replaces the intended output path with the input path (does not actually copy it)
	 */
	COPY,
	/**
	 * Removes the zip from the output
	 */
	SKIP,
	/**
	 * Process the zip as normal
	 */
	CONTINUE
}
