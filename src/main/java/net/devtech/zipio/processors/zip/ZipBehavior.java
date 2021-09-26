package net.devtech.zipio.processors.zip;

public enum ZipBehavior {
	/**
	 * Skip the zip, replacing the intended output path with the input path
	 */
	SKIP,
	/**
	 * Removes the zip from the output
	 */
	NO_OUTPUT,
	/**
	 * Process the zip as normal
	 */
	CONTINUE
}
