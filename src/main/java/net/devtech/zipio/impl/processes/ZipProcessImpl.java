package net.devtech.zipio.impl.processes;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.devtech.zipio.OutputTag;
import net.devtech.zipio.VirtualZipEntry;
import net.devtech.zipio.ZipTag;
import net.devtech.zipio.impl.InternalZipProcess;
import net.devtech.zipio.impl.ProcessingTransferHandler;
import net.devtech.zipio.impl.TransferHandler;
import net.devtech.zipio.impl.ZipTagImpl;
import net.devtech.zipio.impl.util.Lazy;
import net.devtech.zipio.impl.util.U;
import net.devtech.zipio.processes.MemoryZipProcess;
import net.devtech.zipio.processes.ZipProcess;
import net.devtech.zipio.processes.ZipProcessBuilder;
import net.devtech.zipio.processors.entry.ProcessResult;
import net.devtech.zipio.processors.entry.ZipEntryProcessor;
import net.devtech.zipio.processors.zip.PostZipProcessor;
import net.devtech.zipio.processors.zip.ZipBehavior;
import net.devtech.zipio.processors.zip.ZipFilter;
import net.devtech.zipio.stage.TaskTransform;
import net.devtech.zipio.stage.ZipTransform;

public class ZipProcessImpl implements ZipProcessBuilder, InternalZipProcess {
	Process<Void, Void> defaults = new Process<>(null, null);
	List<OutputTag> processed = List.of();
	List<Runnable> afterExecute = List.of();
	List<Process<ZipProcess, UnaryOperator<OutputTag>>> processes = List.of();
	List<ZipProcess> child = List.of();
	List<Process<OutputTag, OutputTag>> zips = List.of();
	List<ZipTagImpl> tags = List.of();
	List<AutoCloseable> closeables = List.of();
	boolean lock;
	ZipEntryProcessor perEntry, postProcess;

	@Override
	public TaskTransform defaults() {
		this.validateLock();
		return this.defaults;
	}

	@Override
	public void addCloseable(Closeable closeable) {
		this.validateLock();
		this.closeables = U.add(this.closeables, closeable);
	}

	@Override
	public TaskTransform linkProcess(ZipProcess process, UnaryOperator<OutputTag> newOutput) {
		this.validateLock();
		var linked = new Process<>(process, newOutput);
		linked.loadDefaults(this.defaults);
		this.processes = U.add(this.processes, linked);
		if(process instanceof ZipProcessImpl z) {
			z.child = U.add(z.child, this);
		}
		return linked;
	}

	@Override
	public ZipTransform addZip(Path path, OutputTag output) {
		this.validateLock();
		var linked = new Process<>(new OutputTag(path), output);
		linked.loadDefaults(this.defaults);
		this.zips = U.add(this.zips, linked);
		return linked;
	}

	@Override
	public void afterExecute(Runnable runnable) {
		this.validateLock();
		this.afterExecute = U.add(this.afterExecute, runnable);
	}

	@Override
	public ZipTag createZipTag(OutputTag outputPath) {
		this.validateLock();
		ZipTagImpl tag = new ZipTagImpl(outputPath);
		this.tags = U.add(this.tags, tag);
		return tag;
	}

	@Override
	public void addProcessed(OutputTag output) {
		this.validateLock();
		this.processed = U.add(this.processed, output);
	}

	@Override
	public void setEntryProcessor(ZipEntryProcessor processor) {
		this.validateLock();
		this.perEntry = processor;
	}

	@Override
	public void setPostProcessor(ZipEntryProcessor handler) {
		this.validateLock();
		this.postProcess = handler;
	}

	@Override
	public void execute() throws IOException {
		Map<Path, FileSystem> toClose = new ConcurrentHashMap<>();
		boolean err;
		try {
			this.execute(toClose, null);
		} finally {
			err = U.close(toClose) | this.close();
		}
		if(err) {
			throw new IOException("Error in closing zip process!");
		}
	}

	@Override
	public MemoryZipProcess executeCached() throws IOException {
		Map<Path, FileSystem> toClose = new ConcurrentHashMap<>();
		// todo add validation to ensure process is closed whenever this is called?
		InMemoryZipProcessImpl process = new InMemoryZipProcessImpl(toClose);
		this.execute(toClose, process);
		this.close();
		return process;
	}


	@Override
	public Iterable<OutputTag> getOutputs() {
		try {
			return this.getPaths();
		} catch(IOException e) {
			throw U.rethrow(e);
		}
	}

	@Override
	public void execute(Map<Path, FileSystem> toClose, Function<OutputTag, TransferHandler> handlerProvider) throws IOException {
		this.lock = true;
		List<ToPostProcessPair> pairs = new ArrayList<>();
		List<VirtualZipEntry> afterAll = this.postProcess == null ? null : new Vector<>();
		for(ZipTagImpl tag : this.tags) {
			tag.handler = this.getHandler(toClose, handlerProvider, tag.getTag());
		}

		ForkJoinPool pool = ForkJoinPool.commonPool();

		List<CompletableFuture<?>> futures = new ArrayList<>();
		for(var iterator = this.zips.iterator(); iterator.hasNext(); ) {
			var zip = iterator.next();
			OutputTag output = zip.b, input = zip.a;
			futures.add(this.invokeFileIO(zip,
					handlerProvider,
					toClose,
					afterAll,
					input,
					output,
					zip.zipPre.apply(zip.a),
					zip.finalizing.apply(zip.a),
					pairs,
					pool
			));
			iterator.remove();
		}

		for(var iterator = this.processes.iterator(); iterator.hasNext(); ) {
			var process = iterator.next();
			if(process.a instanceof InternalZipProcess i) {
				for(OutputTag input : i.processed()) { // this must be first
					futures.add(this.invokeFileIO(process,
							handlerProvider,
							toClose,
							afterAll,
							input,
							process.b.apply(input),
							process.zipPre.apply(input),
							process.finalizing.apply(input),
							pairs,
							pool
					));
				}

				i.execute(toClose, input -> {
					return getHandler(toClose, handlerProvider, pairs, afterAll, process, input);
				});
			} else {
				process.a.execute();
				for(OutputTag input : process.a.getOutputs()) {
					// filter needs to be placed here
					futures.add(this.invokeFileIO(process,
							handlerProvider,
							toClose,
							afterAll,
							input,
							process.b.apply(input),
							process.zipPre.apply(input),
							process.finalizing.apply(input),
							pairs,
							pool
					));
				}
			}
			iterator.remove();
		}

		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
		futures.clear();

		if(afterAll != null) {
			for(VirtualZipEntry impl : afterAll) {
				ProcessResult result = this.postProcess.apply(impl);
				if(result == ProcessResult.POST) {
					throw new IllegalStateException(this.postProcess + " return POST in post handler! (final stage, can't post past final)");
				}
			}
		}

		for(ToPostProcessPair pair : pairs) {
			futures.add(CompletableFuture.runAsync(() -> pair.processor.apply(pair.handler), pool));
		}
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

		if(!this.processes.isEmpty()) {
			this.processes.clear();
		}

		this.afterExecute.forEach(Runnable::run);

		for(Iterator<ZipTagImpl> iterator = this.tags.iterator(); iterator.hasNext(); ) {
			ZipTagImpl tag = iterator.next();
			tag.handler = null;
			iterator.remove();
		}
	}

	@Override
	public Iterable<OutputTag> processed() {
		return this.processed;
	}

	// adding branches is going to be extremely painful
	// we need to account for "loops" n shit too irrrr painnn

	private static void visit(FileSystem in, TransferHandler handler) throws IOException {
		for(Path directory : in.getRootDirectories()) {
			Files.walk(directory).parallel().filter(Files::isRegularFile).forEach(path -> {
				String to = directory.relativize(path).toString();
				handler.copy(to, path);
			});
		}
	}

	private ProcessingTransferHandler getHandler(Map<Path, FileSystem> toClose,
			Function<OutputTag, TransferHandler> handlerProvider,
			List<ToPostProcessPair> pairs,
			List<VirtualZipEntry> afterAll,
			Process<ZipProcess, UnaryOperator<OutputTag>> process,
			OutputTag input) {
		if(input == OutputTag.INPUT) {
			return null;
		}
		// and here
		ZipFilter filter = process.zipPre.apply(input);
		ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, Lazy.empty());
		if(behavior == ZipBehavior.CONTINUE) {
			OutputTag output = process.b.apply(input);
			TransferHandler transfer = this.getHandler(toClose, handlerProvider, output);
			this.processed = U.add(this.processed, output);
			if(transfer != null) {
				PostZipProcessor finalizing = process.finalizing.apply(input);
				if(finalizing != null) {
					pairs.add(new ToPostProcessPair(transfer, finalizing));
				}
				return new ProcessingTransferHandler(transfer,
						afterAll,
						this.perEntry,
						process.entryPost.apply(input),
						process.entryPre.apply(input),
						process.zipPost.apply(input),
						this.postProcess != null
				);
			} else {
				return null;
			}
		} else if(behavior == ZipBehavior.COPY) {
			this.copy(handlerProvider, toClose, input);
			return null;
		} else if(behavior == ZipBehavior.USE_OUTPUT) {
			this.copy(handlerProvider, toClose, process.b.apply(input));
			return null;
		} else {
			return null;
		}
	}

	private boolean close() throws IOException {
		boolean errored = false;
		for(AutoCloseable closeable : this.closeables) {
			try {
				closeable.close();
			} catch(Exception e) {
				errored = true;
				e.printStackTrace();
			}
		}
		if(!this.closeables.isEmpty()) {
			this.closeables.clear();
		}
		return errored;
	}

	private TransferHandler getHandler(Map<Path, FileSystem> systems, Function<OutputTag, TransferHandler> handlerProvider, OutputTag output) {
		if(output == OutputTag.INPUT) {
			return TransferHandler.EMPTY;
		}
		// todo support directories
		TransferHandler system = output.path == null ? null : new TransferHandler.System(systems.computeIfAbsent(output.path, U::createZip), true);
		if(handlerProvider != null) {
			TransferHandler handler = handlerProvider.apply(output);
			if(handler == null) {
				return system;
			} else if(system == null) {
				return handler;
			} else {
				return handler.combine(system);
			}
		} else {
			return system;
		}
	}

	private List<OutputTag> getPaths() throws IOException {
		List<OutputTag> outputs = new ArrayList<>();
		for(ZipTagImpl tag : this.tags) {
			outputs.add(tag.getTag());
		}
		for(var zip : this.zips) {
			if(zip.b != null) {
				this.computeOutput(outputs, zip.zipPre.apply(zip.a), zip.a, zip.b, true);
			}
		}
		for(var process : this.processes) {
			for(OutputTag output : process.a.getOutputs()) {
				if(process.b != null) {
					this.computeOutput(outputs, process.zipPre.apply(output), output, process.b.apply(output), false);
				}
			}
		}
		outputs.addAll(this.processed);
		outputs.removeIf(Objects::isNull);
		outputs.removeIf(o -> o == OutputTag.INPUT);
		return outputs;
	}

	private void computeOutput(List<OutputTag> outputs, ZipFilter processor, OutputTag input, OutputTag output, boolean tempSystem) throws IOException {
		Lazy<FileSystem> system = tempSystem ? new Lazy<>(() -> U.openZip(input.path)) : Lazy.empty();
		try {
			ZipBehavior behavior = processor == null ? ZipBehavior.CONTINUE : processor.test(input, system);
			if(behavior == ZipBehavior.CONTINUE || behavior == ZipBehavior.USE_OUTPUT) {
				outputs.add(output);
			} else if(behavior == ZipBehavior.COPY) {
				outputs.add(input);
			}
		} finally {
			if(system.value != null) {
				system.value.close();
			}
		}
	}

	private CompletableFuture<?> invokeFileIO(Process<?, ?> linked,
			Function<OutputTag, TransferHandler> handlerProvider,
			Map<Path, FileSystem> toClose,
			List<VirtualZipEntry> afterAll,
			OutputTag input,
			OutputTag output,
			ZipFilter filter,
			PostZipProcessor finalizing,
			List<ToPostProcessPair> pairs,
			Executor executor) {
		CompletableFuture<?> future = null;
		Lazy<FileSystem> inputSystem = new Lazy<>(() -> toClose.computeIfAbsent(input.path, U::openZip));
		ZipBehavior behavior = filter == null ? ZipBehavior.CONTINUE : filter.test(input, inputSystem);
		if(behavior == ZipBehavior.CONTINUE) {
			TransferHandler transfer = this.getHandler(toClose, handlerProvider, output);
			if(transfer != null) {
				future = CompletableFuture.runAsync(() -> {
					try(ProcessingTransferHandler processing = new ProcessingTransferHandler(transfer,
							afterAll,
							this.perEntry,
							linked.entryPost.apply(input),
							linked.entryPre.apply(input),
							linked.zipPost.apply(input),
							this.postProcess != null
					)) {
						visit(inputSystem.get(), processing);
					} catch(Exception e1) {
						throw U.rethrow(e1);
					}
				}, executor);

				if(finalizing != null) {
					pairs.add(new ToPostProcessPair(transfer, finalizing));
				}
			}

			if(output.path != null) {
				this.processed = U.add(this.processed, output);
			}
		} else if(behavior == ZipBehavior.COPY) {
			// todo warn if input != output?
			future = CompletableFuture.runAsync(() -> this.copy(handlerProvider, toClose, input), executor);
		} else if(behavior == ZipBehavior.USE_OUTPUT) {
			future = CompletableFuture.runAsync(() -> this.copy(handlerProvider, toClose, output), executor);
		}

		if(future == null) {
			return CompletableFuture.completedFuture(null);
		} else {
			return future;
		}
	}

	private void copy(Function<OutputTag, TransferHandler> handlerProvider, Map<Path, FileSystem> toClose, OutputTag input) {
		TransferHandler temp = handlerProvider == null ? null : handlerProvider.apply(input);
		TransferHandler transfer = this.getHandler(toClose, p -> temp, OutputTag.EMPTY);
		if(transfer != null) {
			FileSystem system = toClose.computeIfAbsent(input.path, U::openZip);
			try {
				visit(system, transfer);
			} catch(IOException e) {
				throw U.rethrow(e);
			}
		}
		this.processed = U.add(this.processed, input);
	}

	void validateLock() {
		if(this.lock) {
			throw new IllegalStateException("ZipProcess was already executed, cannot add new inputs!");
		}
	}

	record ToPostProcessPair(TransferHandler handler, PostZipProcessor processor) {}

	class Process<A, B> implements TaskTransform, ZipTransform {
		final A a;
		final B b;
		Function<OutputTag, ZipFilter> zipPre = p -> ZipFilter.DEFAULT;
		Function<OutputTag, PostZipProcessor> finalizing = p -> null;
		Function<OutputTag, ZipEntryProcessor> entryPost = p -> null, entryPre = p -> null;
		Function<OutputTag, PostZipProcessor> zipPost = p -> null;

		Process(A process, B newOutput) {
			this.a = process;
			this.b = newOutput;
		}

		public void loadDefaults(Process<?, ?> process) {
			this.zipPre = process.zipPre;
			this.finalizing = process.finalizing;
			this.entryPre = process.entryPre;
			this.entryPost = process.entryPost;
			this.zipPost = process.zipPost;
		}

		@Override
		public void setZipFilter(Function<OutputTag, ZipFilter> processor) {
			this.zipPre = processor;
		}

		@Override
		public void setPostZipProcessor(Function<OutputTag, PostZipProcessor> processor) {
			this.zipPost = processor;
		}

		@Override
		public void setPostEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor) {
			this.entryPost = processor;
		}

		@Override
		public void setPreEntryProcessor(Function<OutputTag, ZipEntryProcessor> processor) {
			this.entryPre = processor;
		}

		@Override
		public void setFinalizingZipProcessor(Function<OutputTag, PostZipProcessor> processor) {
			this.finalizing = processor;
		}
	}
}
