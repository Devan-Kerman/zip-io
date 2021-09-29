package net.devtech.zipio.stage;

import java.nio.file.Path;

import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.PostZipProcessor;
import net.devtech.zipio.processors.zip.ZipFilter;

public interface ZipTransform extends TaskTransform {
	default void setPostEntryProcessor(ZipEntryProcessor processor) {
		this.setPostEntryProcessor((Path p) -> processor);
	}

	default void setPreEntryProcessor(ZipEntryProcessor processor) {
		this.setPreEntryProcessor((Path p) -> processor);
	}

	default void setZipFilter(ZipFilter processor) {
		this.setZipFilter(p -> processor);
	}

	default void setPostZipProcessor(PostZipProcessor processor) {
		this.setPostZipProcessor(p -> processor);
	}
	// todo zip skipper: something here that is passed the input file system (or just the path + lazy fs) and if it skips then add the input to output
}
