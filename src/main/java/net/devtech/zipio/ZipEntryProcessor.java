package net.devtech.zipio;

public interface ZipEntryProcessor {
	ProcessResult apply(VirtualZipEntry buffer);
}
