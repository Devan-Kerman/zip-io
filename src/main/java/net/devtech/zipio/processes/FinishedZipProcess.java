package net.devtech.zipio.processes;

import java.io.IOException;

public interface FinishedZipProcess extends ZipProcess {
	@Override
	default void execute() throws IOException {}
}
