package net.devtech.zipio.processes;

/**
 * An in-memory representation of the outputs of a zip process
 */
public interface MemoryZipProcess extends FinishedZipProcess, AutoCloseable {
	/**
	 * Releases any held resources by the process, this must be called!
	 */
	@Override
	void close() throws Exception;
}
