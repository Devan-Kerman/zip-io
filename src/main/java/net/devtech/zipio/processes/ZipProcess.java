package net.devtech.zipio.processes;

import java.io.IOException;
import java.nio.file.Path;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.impl.processes.EmptyZipProcessImpl;
import net.devtech.zipio.impl.processes.ZipProcessImpl;

public interface ZipProcess {
	static ZipProcessBuilder builder() {
		return new ZipProcessImpl();
	}

	static FinishedZipProcess empty() {
		return EmptyZipProcessImpl.INSTANCE;
	}

	/**
	 * Execute the zip process as a streaming process, this means that if the execute function is called again,
	 *  nothing happens, and if the ZipProcess is linked, it will read this process's outputs from the disk.
	 */
	void execute() throws IOException;

	/**
	 * Execute the zip and store an in-memory representation of the outputs, this means if the returned zip process is linked, it wont have
	 *  to read from the disk
	 */
	MemoryZipProcess executeCached() throws IOException;

	/**
	 * @return the current outputs of the ZipProcess, files may or may not exist when this method is invoked.
	 *  After {@link #execute()} is called, they *should* exist.
	 */
	Iterable<OutputTag> getOutputs();
}
