package net.devtech.zipio;

import java.nio.file.Path;
import java.util.function.Function;

public interface ZipTag extends ZipOutput {
	/**
	 * @return if the zip tag is written too before this is true, it will throw
	 */
	boolean isExecuting();

	OutputTag getTag();
}
