package net.devtech.zipio;

public interface ZipProcessable {
	/**
	 * @param processor This processor is fired for every entry with {@link ProcessResult#POST} (or for every entry if there is no entry processor) in this object
	 */
	void setZipProcessor(ZipEntryProcessor processor);
}
