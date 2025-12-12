package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.util.collection.IndexedIterable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RegistryPalette<T> implements IntIterable {
	private final Registry<T> registry;
	private final int[] raw;
	private final int[] inverse;
	private final ValueView valueView;
	private int size;

	public RegistryPalette(Registry<T> registry) {
		this.registry = registry;
		this.raw = ArrayUtil.ofSingle(-1, registry.size());
		this.inverse = ArrayUtil.ofSingle(-1, registry.size());
		this.size = 0;
		this.valueView = new ValueView();
	}

	public int find(int value) {
		return inverse[value];
	}

	private int add(int value) {
		raw[size] = value;
		inverse[value] = size;
		T object = registry.get(value);
		valueView.values.add(object);
		size++;
		return size - 1;
	}

	public int findOrAdd(int value) {
		int index = find(value);
		return index == -1 ? add(value) : index;
	}

	public int findOrAdd(T value) {
		return findOrAdd(registry.getRawId(value));
	}

	public int get(int index) {
		return raw[index];
	}

	public @NotNull IntIterator iterator() {
		return IntIterators.wrap(raw, 0, size);
	}

	public ValueView view() {
		return valueView;
	}

	public class ValueView implements IndexedIterable<T> {
		private final T defaultValue = registry instanceof DefaultedRegistry<T> defreg ? defreg.get(defreg.getDefaultId()) : registry.get(0);
		private final List<T> values = new ArrayList<>();
		private boolean errored = false;

		public Registry<T> registry() {
			return registry;
		}

		@Override
		public T get(int index) {
			if (index >= values.size()) {
				if (!errored) {
					Surveyor.LOGGER.error("[Surveyor] Palette view access at index {} for palette size {}! Returning garbage!", index, values.size(), new IllegalStateException("garbage palette"));
					errored = true;
				}
				return defaultValue;
			}
			return values.get(index);
		}

		@Override
		public int getRawId(T value) {
			return values.indexOf(value);
		}

		@Override
		public @NotNull Iterator<T> iterator() {
			return values.iterator();
		}

		@Override
		public int size() {
			return size;
		}
	}
}
