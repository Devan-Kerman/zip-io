package net.devtech.zipio;

public interface ZipTag extends ZipOutput {
	/**
	 * @return if the zip tag is written too before this is true, it will throw
	 */
	boolean isExecuting();
}
