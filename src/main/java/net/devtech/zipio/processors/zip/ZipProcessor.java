package net.devtech.zipio.processors.zip;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.devtech.zipio.processes.ZipProcess;
import net.devtech.zipio.processes.ZipProcessBuilder;

public interface ZipProcessor {
	/**
	 * Customize behavior for a specific zip based on the input path
	 *
	 * Test behavior for the whole zip at once, in {@link ZipProcessBuilder#linkProcess(ZipProcess, UnaryOperator)}s the file likely has not been created yet, and attempting to get the file
	 *  system will return null in order to prevent an outdated system from being used
	 *
	 * @param file may not exist, may even be null
	 * @param system The file system of the path, may return null
	 */
	ZipBehavior test(Path file, Supplier<FileSystem> system);

	// todo instead of Path, pass something that gives URI, URL, Path, String, etc.
	// remember we need to pass the input path and not the output path
}
