package net.devtech.zipio.impl.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class U {
	private static final Map<String, ?> NIO_CREATE = Map.of("create", "true");

	public static ByteBuffer read(Path p) {
		ByteBuffer curr = BufferPool.INSTANCE.get();
		try(SeekableByteChannel stream = Files.newByteChannel(p)) {
			int poolRet = 0;
			while(stream.read(curr) != -1) {
				if(curr.remaining() == 0) { // if buffer is full
					ByteBuffer old = curr;
					curr = ByteBuffer.allocate(curr.capacity() * 2);
					old.get(curr.array(), 0, old.position());
					curr.position(old.position());
					poolRet = BufferPool.INSTANCE.ret(old, poolRet);
				}
			}
		} catch(IOException e) {
			throw rethrow(e);
		}
		return curr;
	}

	public static <T> List<T> lazy(List<T> curr) {
		return (curr == null || curr.isEmpty()) ? new ArrayList<>() : curr;
	}

	public static <T> List<T> add(List<T> list, T value) {
		List<T> handlers = lazy(list);
		handlers.add(value);
		return handlers;
	}

	public static FileSystem createZip(Path path) {
		try {
			Files.deleteIfExists(path);
			return FileSystems.newFileSystem(path, NIO_CREATE);
		} catch(IOException e) {
			throw rethrow(e);
		}
	}

	/**
	 * @return nothing, because it throws
	 * @throws T rethrows {@code throwable}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> RuntimeException rethrow(Throwable throwable) throws T {
		throw (T) throwable;
	}
}
