package net.devtech.zipio.stage;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.PostZipProcessor;
import net.devtech.zipio.processors.zip.ZipFilter;

public interface TaskTransform {
	/**
	 * @param processor input is the input file from the process, may not exist
	 */
	void setZipFilter(Function<OutputTag, ZipFilter> processor);

	/**
	 * @param processor This processor is fired for every entry with {@link ProcessResult#POST} (or for every entry if there is no entry processor) in this object
	 */
	void setPostZipProcessor(Function<OutputTag, PostZipProcessor> processor);

	/**
	 * This is fired before {@link ZipProcessBuilder#setEntryProcessor(ZipEntryProcessor)}, returning post will delegate further processing to the process' entry processor
	 */
	void setPostEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor);

	/**
	 * This does not work if the output is null, does not throw an exception, it just does not get called
	 */
	void setPreEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor);

	void setProcessOnly(Predicate<OutputTag> processOnly);


}
