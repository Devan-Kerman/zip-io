package net.devtech.zipio.impl;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;

import net.devtech.zipio.impl.entry.CopyWriteZipEntryImpl;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.impl.entry.InMemoryZipEntryImpl;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.processors.zip.PostZipProcessor;

public class ProcessingTransferHandler implements TransferHandler {
	final TransferHandler handler;
	final List<VirtualZipEntry> afterAll, postEntries;
	final ZipEntryProcessor pre, per, post;
	final PostZipProcessor postZip;
	final boolean hasProcessPostProcessor;
	boolean hasPost;

	public ProcessingTransferHandler(TransferHandler handler,
			List<VirtualZipEntry> all,
			ZipEntryProcessor per,
			ZipEntryProcessor post,
			ZipEntryProcessor pre,
			PostZipProcessor postZip,
			boolean hasPostProcessor) {
		this.hasProcessPostProcessor = hasPostProcessor;
		this.handler = handler;
		this.postEntries = post == null ? null : new Vector<>();
		this.afterAll = all;
		this.per = per;
		this.post = post;
		this.pre = pre;
		this.postZip = postZip;
	}

	@Override
	public void copy(String destination, Path path) {
		RealZipEntry impl = new RealZipEntry(this.handler, path, destination);
		this.applyIndividualEntry(impl);
	}

	@Override
	public void write(String destination, ByteBuffer buffer) {
		InMemoryZipEntryImpl impl = new InMemoryZipEntryImpl(this.handler, buffer, destination);
		this.applyIndividualEntry(impl);
	}

	@Override
	public void copyWrite(String destination, Path compressedData, ByteBuffer uncompressedData) {
		CopyWriteZipEntryImpl copy = new CopyWriteZipEntryImpl(this.handler, destination, compressedData, uncompressedData);
		this.applyIndividualEntry(copy);
	}

	private void applyIndividualEntry(VirtualZipEntry impl) {
		if((this.pre == null ? ProcessResult.POST : this.pre.apply(impl)) == ProcessResult.POST) {
			ProcessResult result = this.per == null ? ProcessResult.result(this.post != null || this.hasProcessPostProcessor) : this.per.apply(impl);
			if(result == ProcessResult.POST) {
				if(this.postEntries != null) {
					this.postEntries.add(impl);
				} else if(this.afterAll != null) {
					this.afterAll.add(impl);
				} else {
					throw new IllegalStateException((this.post + "/" + this.pre) + " return POST, yet no post handlers exist!");
				}
				this.hasPost = true;
			}
		}
	}

	@Override
	public void close() throws Exception {
		if(this.postEntries != null) {
			this.hasPost = false;
			for(VirtualZipEntry impl : this.postEntries) {
				ProcessResult result = this.post == null ? ProcessResult.POST : this.post.apply(impl);
				if(result == ProcessResult.POST) {
					if(this.afterAll != null) {
						this.afterAll.add(impl);
						this.hasPost = true;
					} else {
						throw new IllegalStateException(this.post + " return POST, yet no post handlers exist!");
					}
				}
			}
		}
		if(this.postZip != null) {
			this.postZip.apply(this.handler);
		}
		this.handler.close();
	}
}
