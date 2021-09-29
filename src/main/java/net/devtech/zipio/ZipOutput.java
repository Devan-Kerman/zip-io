package net.devtech.zipio;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public interface ZipOutput {
	/**
	 * Writes the given data to the given path in the output zip
	 */
	void write(String fileName, ByteBuffer buffer);

	/**
	 * Copies the path to the path(fileName) in the output zip
	 */
	void copy(String fileName, Path input);
}
