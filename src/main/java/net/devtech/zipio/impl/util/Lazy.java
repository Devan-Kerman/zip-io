package net.devtech.zipio.impl.util;

import java.util.function.Supplier;

public class Lazy<T> implements Supplier<T> {
	public static final Lazy<?> EMPTY = new Lazy<>(null);

	public static <T> Lazy<T> empty() {
		return (Lazy<T>) EMPTY;
	}

	public Supplier<T> supplier;
	public T value;

	public Lazy(Supplier<T> supplier) {
		this.supplier = supplier;
	}

	@Override
	public T get() {
		if(this.supplier == null) {
			return this.value;
		}
		T val = this.value = this.supplier.get();
		this.supplier = null;
		return val;
	}
}
