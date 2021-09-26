package test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.impl.processes.ZipProcessImpl;

public class Test {
	public static void main(String[] args) throws IOException, InterruptedException {
		URL url = new URL("https://www.google.com/");

		Thread.sleep(10000);
		for(int i = 0; i < 10; i++) {
			ZipProcessImpl removeResourcesProcess = new ZipProcessImpl();
			removeResourcesProcess.addZip(Path.of("input.jar"), Path.of("output.jar"));
			removeResourcesProcess.setEntryProcessor(buffer -> {
				if(buffer.path().endsWith(".class")) {
					buffer.copyToOutput();
				}
				return ProcessResult.HANDLED;
			});
			removeResourcesProcess.execute();
		}
	}
}
