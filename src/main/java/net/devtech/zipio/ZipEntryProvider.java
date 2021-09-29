package net.devtech.zipio;

import java.util.function.Consumer;

public interface ZipEntryProvider extends AutoCloseable {
	void accept(Consumer<ZipEntryProvider> providers);

	@Override
	default void close() throws Exception {}

	class Zip implements ZipEntryProvider {


		@Override
		public void accept(Consumer<ZipEntryProvider> providers) {

		}
	}
}
