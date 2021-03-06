package test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.collect.Iterators;
import com.google.common.jimfs.Jimfs;
import net.devtech.zipio.impl.processes.ZipProcessImpl;
import net.devtech.zipio.impl.util.U;
import net.devtech.zipio.processors.entry.ProcessResult;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class Test {

	public static void downloadToRandomAccessFile(URL url, Path output) throws IOException {
		File temp = File.createTempFile("zip_io_devtech", ".zip");
		RandomAccessFile file = new RandomAccessFile(temp, "rw");
		ReadableByteChannel input = Channels.newChannel(url.openStream());
		FileChannel stream = file.getChannel();
		stream.transferFrom(input, 0, Long.MAX_VALUE);
		ZipFile zip = new ZipFile(file.getChannel());
		try(FileSystem write = U.createZip(output)) {
			stream(zip.getEntries()).parallel().forEach(z -> {
				try {
					String name = z.getName();
					Path dest = write.getPath(name);
					if(name.endsWith(".class")) {
						U.createDirs(dest);
						Files.copy(zip.getInputStream(z), dest);
					}
				} catch(IOException e) {
					throw U.rethrow(e);
				}
			});
		}
	}

	public static <T> Stream<T> stream(Enumeration<T> e) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(Iterators.forEnumeration(e), Spliterator.ORDERED), false);
	}

	public static void downloadToVirtualSystem(URL url, Path output) throws IOException {
		FileSystem memory = Jimfs.newFileSystem();
		Path temp = memory.getPath("temp.jar");
		Files.copy(url.openStream(), temp);
		try(FileSystem system = U.openZip(temp)) {
			for(Path directory : system.getRootDirectories()) {
				Files.walk(directory)
						.filter(p -> !p.toString().endsWith(".class"))
						.filter(Files::isRegularFile)
						.forEach(f -> {
							try {
								Files.delete(f);
							} catch(IOException e) {
								throw new RuntimeException(e);
							}
						});
			}
		}

		/*ZipProcessImpl remove = new ZipProcessImpl();
		remove.addZip(temp, output);
		remove.setEntryProcessor(buffer -> {
			if(buffer.path().endsWith(".class")) {
				buffer.copyToOutput();
			}
			return ProcessResult.HANDLED;
		});
		remove.execute();*/
	}

	public static void downloadReadThenOperate(URL url, Path output) throws IOException {
		// download
		ReadableByteChannel input = Channels.newChannel(url.openStream());
		File temporary = File.createTempFile("devtech_zipio", ".zip");
		FileChannel stream = new FileOutputStream(temporary).getChannel();
		stream.transferFrom(input, 0, Long.MAX_VALUE);

		// process
		ZipProcessImpl remove = new ZipProcessImpl();
		remove.addZip(temporary.toPath(), output);
		remove.setEntryProcessor(buffer -> {
			if(buffer.path().endsWith(".class")) {
				buffer.copyToOutput();
			}
			return ProcessResult.HANDLED;
		});
		remove.execute();
	}

	public static void operateAsDownload(URL url, Path output) throws IOException {
		try(FileSystem out = U.createZip(output)) {
			ZipInputStream input = new ZipInputStream(url.openStream());
			ZipEntry entry;
			while((entry = input.getNextEntry()) != null) {
				if(entry.getName().endsWith(".class")) {
					Path path = out.getPath(entry.getName());
					U.createDirs(path);
					Files.copy(input, path);
				}
			}
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		URL url = new URL("https://launcher.mojang.com/v1/objects/8d9b65467c7913fcf6f5b2e729d44a1e00fde150/client.jar");
		Path test = Path.of("test.jar");

		for(int i = 0; i < 10; i++) {
			//extracted(url, test, "RAF", Test::downloadToRandomAccessFile);
			extracted(url, test, "OAD", Test::operateAsDownload);
			extracted(url, test, "JIM", Test::downloadToVirtualSystem);
			//extracted(url, test, "DTO", Test::downloadReadThenOperate);

		}

		System.out.println("jit can my balls");

		long totalJim = 0, totalOAD = 0;
		for(int i = 0; i < 10; i++) {
			//extracted(url, test, "RAF", Test::downloadToRandomAccessFile);
			totalOAD += extracted(url, test, "OAD", Test::operateAsDownload);
			totalJim += extracted(url, test, "JIM", Test::downloadToVirtualSystem);
			//extracted(url, test, "DTO", Test::downloadReadThenOperate);

		}
		System.out.println(totalJim + " " + totalOAD);
	}

	interface Act {
		void run(URL url, Path output) throws IOException;
	}

	private static long extracted(URL url, Path out, String name, Act act) throws IOException {
		long start = System.currentTimeMillis();
		act.run(url, out);
		long time = System.currentTimeMillis() - start;
		System.out.println(name + ": " + time);
		return time;
	}
}
