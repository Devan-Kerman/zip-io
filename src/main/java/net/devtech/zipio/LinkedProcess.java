package net.devtech.zipio;

import java.nio.file.Path;
import java.util.function.Predicate;

public interface LinkedProcess extends ZipProcessable {
	/**
	 * When executing, this filter determines whether or not to pass the given input into our handlers
	 */
	void setFilter(Predicate<Path> path);
}
