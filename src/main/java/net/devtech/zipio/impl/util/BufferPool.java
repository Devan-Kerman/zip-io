package net.devtech.zipio.impl.util;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A *mostly* threadsafe buffer pool, the chance of a collision is incredibly low though
 */
public final class BufferPool {
	public static final BufferPool INSTANCE = new BufferPool();
	static final int SIZE = 1024;
	static final int MASK = SIZE - 1;
	final AtomicInteger index = new AtomicInteger(-1);
	final AtomicReferenceArray<ByteBuffer> buffers = new AtomicReferenceArray<>(SIZE);

	public ByteBuffer get() {
		ByteBuffer buf = null;
		for(int i = 0; i < SIZE && (buf = this.buffers.getAndSet(this.index.incrementAndGet() & MASK, null)) == null; i++) {
		}
		return buf == null ? ByteBuffer.allocate(4096) : buf;
	}

	public int ret(ByteBuffer buf, int start) {
		buf.position(0);
		int i;
		for(i = 0; i < SIZE && !this.buffers.compareAndSet((start + i) & MASK, null, buf); i++) {
		}
		// buffer was just returned to the given index, optimize case
		if(i != SIZE) {
			this.index.set(i);
			return i;
		} else {
			return 0;
		}
	}
}