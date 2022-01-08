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
	CONTINUE,
	/**
	 * Like copy, but instead of using the input path, it uses the output path.
	 * This is useful when that specific artifact was already processed earlier
	 */
	USE_OUTPUT
}
