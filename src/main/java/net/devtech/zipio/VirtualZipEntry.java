package net.devtech.zipio;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public interface VirtualZipEntry {
	String path();

	ByteBuffer read();

	void copyToOutput();

	void copyTo(String fileName);

	void write(String fileName, ByteBuffer buffer);

	void copyTo(Path path);
}
