package net.devtech.zipio.impl.entry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;

import net.devtech.zipio.ZipTag;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.ZipTagImpl;
import net.devtech.zipio.impl.util.U;

public class CopyWriteZipEntryImpl extends AbstractVirtualZipEntry {
	final Path compressedPath;
	final ByteBuffer uncompressedData;

	public CopyWriteZipEntryImpl(TransferHandler handler, String destination, Path path, ByteBuffer data) {
		super(handler, destination);
		this.compressedPath = path;
		this.uncompressedData = data;
	}

	@Override
	public ByteBuffer read() {
		return this.uncompressedData;
	}

	@Override
	public void copyToOutput() {
		this.handler.copyWrite(this.destination, this.compressedPath, this.uncompressedData);
	}

	@Override
	public void copyTo(String fileName) {
		this.handler.copyWrite(fileName, this.compressedPath, this.uncompressedData);
	}

	@Override
	public void copyTo(Path path) {
		FileSystemProvider provider = provider(this.compressedPath);
		try {
			if(provider(path) == provider) {
				// same provider
				provider.copy(this.compressedPath, path);
			} else {
				// different providers
				this.uncompressedData.rewind();
				Files.newByteChannel(path).write(this.uncompressedData);
			}
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	static FileSystemProvider provider(Path path) {
		return path.getFileSystem().provider();
	}

	@Override
	public void copyTo(String path, ZipTag tag) {
		if(tag instanceof ZipTagImpl z) {
			z.copyWrite(path, this.compressedPath, this.uncompressedData);
		} else {
			tag.copy(path, this.compressedPath);
		}
	}
}
