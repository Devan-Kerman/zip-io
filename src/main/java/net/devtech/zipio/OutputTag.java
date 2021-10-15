package net.devtech.zipio;

import java.nio.file.Path;

import net.devtech.zipio.stage.ZipTransform;

/**
 * @see ZipTransform
 */
public class OutputTag {
	public static final OutputTag EMPTY = new OutputTag(null);

	/**
	 * states the tag is an input, just for scanning or whatever else, this means it will not be passed to any ZipProcesses that depend on it
	 */
	public static final OutputTag INPUT = new OutputTag(null);

	/**
	 * may be null
	 */
	public final Path path;

	public OutputTag(Path path) {
		this.path = path;
	}

	public Path getPath() {
		return this.path;
	}

	public Path getVirtualPath() {
		return this.path;
	}

	/**
	 * This is used for cases where you want to pass the given output into any linked processes, but you don't want to output any data to the disk
	 */
	public static class Virtual extends OutputTag {
		public final Path virtualPath;

		public Virtual(Path path) {
			super(null);
			this.virtualPath = path;
		}

		@Override
		public Path getVirtualPath() {
			return this.virtualPath;
		}
	}
}
