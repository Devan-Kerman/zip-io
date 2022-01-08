package net.devtech.zipio.stage;

import java.nio.file.Path;
import java.util.function.Function;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.PostZipProcessor;
import net.devtech.zipio.processors.zip.ZipFilter;

public interface ZipTransform extends TaskTransform {
	// todo output tags

	default void setPostEntryProcessor(ZipEntryProcessor processor) {
		this.setPostEntryProcessor((OutputTag p) -> processor);
	}

	default void setPreEntryProcessor(ZipEntryProcessor processor) {
		this.setPreEntryProcessor((OutputTag p) -> processor);
	}

	default void setZipFilter(ZipFilter processor) {
		this.setZipFilter(p -> processor);
	}

	default void setPostZipProcessor(PostZipProcessor processor) {
		this.setPostZipProcessor(p -> processor);
	}

	default void setFinalizingZipProcessor(PostZipProcessor processor) {
		this.setFinalizingZipProcessor(p -> processor);
	}
}
