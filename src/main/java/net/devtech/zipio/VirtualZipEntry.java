package net.devtech.zipio;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public interface VirtualZipEntry {
	/**
	 * @return The path to the zip entry
	 */
	String path();

	/**
	 * @return The lazily evaluated contents of the zip entry, do not modify this buffer!
	 */
	ByteBuffer read();

	/**
	 * Copies the zip entry to the same path in the output zip
	 */
	void copyToOutput();

	/**
	 * Writes the given data to the same path as this entry into the output zip
	 */
	void writeToOutput(ByteBuffer buffer);

	/**
	 * Copies this entry to the given path in the output zip
	 */
	void copyTo(String fileName);

	/**
	 * Writes the given data to the given path in the output zip
	 */
	void write(String fileName, ByteBuffer buffer);

	/**
	 * Copies the path to the path(fileName) in the output zip
	 */
	void copy(String fileName, Path input);

	/**
	 * Copy the current zip entry to the given path
	 */
	void copyTo(Path path);

	void copyTo(String path, ZipTag tag);
}
