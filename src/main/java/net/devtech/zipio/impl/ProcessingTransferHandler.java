package net.devtech.zipio.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;

import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.impl.entry.InMemoryZipEntryImpl;
import net.devtech.zipio.impl.entry.RealZipEntry;
import net.devtech.zipio.impl.util.BufferPool;

public class ProcessingTransferHandler implements TransferHandler {
	final TransferHandler handler;
	final List<VirtualZipEntry> afterAll, postEntries;
	final ZipEntryProcessor per, post;
	boolean hasPost;

	public ProcessingTransferHandler(TransferHandler handler, List<VirtualZipEntry> all, ZipEntryProcessor per, ZipEntryProcessor post) {
		this.handler = handler;
		this.postEntries = post == null ? null : new Vector<>();
		this.afterAll = all;
		this.per = per;
		this.post = post;
	}

	@Override
	public void copyFrom(Path path, String destination) throws IOException {
		RealZipEntry impl = new RealZipEntry(this.handler, path, destination);
		this.applyIndividualEntry(impl);
	}

	@Override
	public void write(ByteBuffer buffer, String destination) {
		InMemoryZipEntryImpl impl = new InMemoryZipEntryImpl(this.handler, buffer, destination);
		this.applyIndividualEntry(impl);
	}

	private void applyIndividualEntry(VirtualZipEntry impl) {
		ZipEntryProcessor process = this.per;
		ProcessResult result = process.apply(impl);
		if(result == ProcessResult.POST) {
			if(this.postEntries != null) {
				this.postEntries.add(impl);
			} else if(this.afterAll != null) {
				this.afterAll.add(impl);
			} else {
				throw new IllegalStateException(process + " return POST, yet no post handlers exist!");
			}
			this.hasPost = true;
		} else if(impl instanceof RealZipEntry i && i.lazy.supplier != null) {
			BufferPool.INSTANCE.ret(i.lazy.value, 0);
		}
	}

	@Override
	public void close() throws Exception {
		if(this.postEntries != null) {
			this.hasPost = false;
			for(VirtualZipEntry impl : this.postEntries) {
				ProcessResult result = this.post.apply(impl);
				if(result == ProcessResult.POST) {
					if(this.afterAll != null) {
						this.afterAll.add(impl);
						this.hasPost = true;
					} else {
						throw new IllegalStateException(this.post + " return POST, yet no post handlers exist!");
					}
				} else if(impl instanceof RealZipEntry i && i.lazy.supplier != null) {
					BufferPool.INSTANCE.ret(i.lazy.value, 0);
				}
			}
		}
		this.handler.close();
	}
}
