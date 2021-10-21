package net.devtech.zipio.impl.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class U {
	private static final Map<String, ?> NIO_CREATE = Map.of("create", "true");

	public static boolean close(Map<Path, FileSystem> systems) {
		Iterator<FileSystem> iterator = systems.values().iterator();
		boolean fail = false;
		while(iterator.hasNext()) {
			FileSystem value = iterator.next();
			iterator.remove();
			try {
				value.close();
			} catch(IOException e) {
				fail = true;
				e.printStackTrace();
			}
		}
		return fail;
	}

	public static ByteBuffer read(ReadableByteChannel stream) {
		ByteBuffer curr = ByteBuffer.allocate(4096);
		try {
			while(stream.read(curr) != -1) {
				if(curr.remaining() == 0) { // if buffer is full
					int lim = curr.limit();
					ByteBuffer clone = ByteBuffer.allocate(lim * 2);
					clone.put(0, curr, 0, lim);
					clone.position(curr.position());
					curr = clone;
				}
			}
		} catch(IOException e) {
			throw rethrow(e);
		}
		curr.limit(curr.limit() - curr.remaining());
		curr.rewind();
		return curr;
	}

	public static ByteBuffer read(Path p) {
		try(SeekableByteChannel stream = Files.newByteChannel(p)) {
			return read(stream);
		} catch(IOException e) {
			throw rethrow(e);
		}
	}

	public static <T> List<T> lazy(List<T> curr) {
		return (curr == null || curr.isEmpty()) ? new ArrayList<>() : curr;
	}

	public static <T> List<T> add(List<T> list, T value) {
		List<T> handlers = lazy(list);
		handlers.add(value);
		return handlers;
	}

	public static FileSystem openZip(Path path) {
		if(path == null) {
			return null;
		}
		try {
			return FileSystems.newFileSystem(path);
		} catch(IOException e) {
			throw rethrow(e);
		}
	}

	public static FileSystem createZip(Path path) {
		if(path == null) {
			return null;
		}
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

	public static void createDirs(Path path) throws IOException {
		Path parent = path.getParent();
		if(parent != null) {
			Files.createDirectories(parent);
		}
	}
}
