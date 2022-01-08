package net.devtech.zipio.impl.processes;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.processes.ZipProcess;

public class ZipProcessTreeExecutor {
	final Map<ZipProcessImpl, List<TransferHandler>> handlers = new HashMap<>();
	public void execute(ZipProcessImpl impl) {

		List<ZipProcessImpl> childless = new ArrayList<>();
		findChildlessParents(impl, new HashSet<>(), childless);
		for(ZipProcessImpl process : childless) {
			for(var op : process.processes) {

			}
		}
	}

	private TransferHandler createHandler(ZipProcessImpl.Process<ZipProcess, UnaryOperator<OutputTag>> process) {
		return null;
	}

	static void findChildlessParents(ZipProcessImpl this_, Set<ZipProcessImpl> visited, List<ZipProcessImpl> childless) {
		if(visited.add(this_)) {
			if(this_.processes.isEmpty() || this_.processes.stream().anyMatch(z -> z.a instanceof ZipProcessImpl)) {
				childless.add(this_);
				return;
			}
			for(ZipProcess process : this_.child) {
				if(process instanceof ZipProcessImpl i) {
					findChildlessParents(i, visited, childless);
				}
			}
			for(ZipProcessImpl.Process<ZipProcess, UnaryOperator<OutputTag>> process : this_.processes) {
				if(process.a instanceof ZipProcessImpl i) {
					findChildlessParents(i, visited, childless);
				}
			}
		}
	}
}
