package test;

import java.io.IOException;
import java.nio.file.Path;

import net.devtech.zipio.ProcessResult;
import net.devtech.zipio.impl.ZipProcessImpl;

public class Test {
	public static void main(String[] args) throws IOException, InterruptedException {
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
