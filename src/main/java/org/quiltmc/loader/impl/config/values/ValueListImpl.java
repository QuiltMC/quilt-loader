/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.config.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ComplexConfigValue;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;

public final class ValueListImpl<T> implements ValueList<T>, org.quiltmc.loader.api.config.values.CompoundConfigValue<T> {
	private final T defaultValue;
	private final List<T> values;

	private TrackedValueImpl<?> configValue;

	public ValueListImpl(T defaultValue, List<T> values) {
		this.defaultValue = defaultValue;
		this.values = values;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ValueList<T> copy() {
		List<T> values = new ArrayList<>(this.values.size());

		for (T value : this.values) {
			if (value instanceof CompoundConfigValue) {
				values.add((T) ((CompoundConfigValue<?>) value).copy());
			} else {
				values.add(value);
			}
		}

		ValueListImpl<T> result = new ValueListImpl<>(this.defaultValue, values);

		result.setValue(this.configValue);

		return result;
	}

	@Override
	public void setValue(TrackedValue<?> configValue) {
		this.configValue = (TrackedValueImpl<?>) configValue;

		if (this.defaultValue instanceof ComplexConfigValue) {
			for (T value : this.values) {
				((ComplexConfigValue) value).setValue(configValue);
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<T> getType() {
		return (Class<T>) this.defaultValue.getClass();
	}

	@Override
	public T getDefaultValue() {
		return this.defaultValue;
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return values.contains(o);
	}

	@NotNull
	@Override
	public Iterator<T> iterator() {
		return values.iterator();
	}

	@Override
	public Object @NotNull [] toArray() {
		return values.toArray();
	}

	@Override
	public <T1> T1 @NotNull [] toArray(@NotNull T1 @NotNull [] a) {
		return values.toArray(a);
	}

	@Override
	public boolean add(T t) {
		values.add(t);

		this.configValue.updateAndSerialize();

		return true;
	}

	@Override
	public boolean remove(Object o) {
		boolean r = values.remove(o);

		if (r) {
			this.configValue.updateAndSerialize();
		}

		return r;
	}

	@Override
	public boolean containsAll(@NotNull Collection<?> c) {
		return values.containsAll(c);
	}

	@Override
	public boolean addAll(@NotNull Collection<? extends T> c) {
		boolean v = values.addAll(c);

		if (v) {
			this.configValue.updateAndSerialize();
		}

		return v;
	}

	@Override
	public boolean addAll(int index, @NotNull Collection<? extends T> c) {
		boolean v = values.addAll(index, c);

		if (v) {
			this.configValue.updateAndSerialize();
		}

		return v;
	}

	@Override
	public boolean removeAll(@NotNull Collection<?> c) {
		boolean v = values.removeAll(c);

		if (v) {
			this.configValue.updateAndSerialize();
		}

		return v;
	}

	@Override
	public boolean retainAll(@NotNull Collection<?> c) {
		boolean v = values.retainAll(c);

		if (v) {
			this.configValue.updateAndSerialize();
		}

		return v;
	}

	@Override
	public void replaceAll(UnaryOperator<T> operator) {
		values.replaceAll(operator);

		this.configValue.updateAndSerialize();
	}

	@Override
	public void sort(Comparator<? super T> c) {
		values.sort(c);

		this.configValue.updateAndSerialize();
	}

	@Override
	public void clear() {
		if (this.isEmpty()) {
			values.clear();
		} else {
			values.clear();
			this.configValue.updateAndSerialize();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ValueListImpl<?>) {
			ValueListImpl<?> a = (ValueListImpl<?>) o;

			return a.defaultValue.equals(this.defaultValue) && a.values.equals(this.values);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return values.hashCode();
	}

	@Override
	public T get(int index) {
		return values.get(index);
	}

	@Override
	public T set(int index, T value) {
		T v = values.set(index, value);

		if (value instanceof ComplexConfigValue) {
			((ComplexConfigValue) value).setValue(this.configValue);
		}

		if ((v != null && value != null && !v.equals(value)) || (v != null && value == null) || (v == null && value != null)) {
			this.configValue.updateAndSerialize();
		}

		return v;
	}

	@Override
	public void add(int index, T value) {
		values.add(index, value);

		this.configValue.updateAndSerialize();
	}

	@Override
	public T remove(int index) {
		T v = values.remove(index);

		this.configValue.updateAndSerialize();

		return v;
	}

	@Override
	public int indexOf(Object o) {
		return values.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return values.lastIndexOf(o);
	}

	@NotNull
	@Override
	public ListIterator<T> listIterator() {
		return values.listIterator();
	}

	@NotNull
	@Override
	public ListIterator<T> listIterator(int index) {
		return values.listIterator(index);
	}

	@NotNull
	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		return values.subList(fromIndex, toIndex);
	}

	@Override
	public Spliterator<T> spliterator() {
		return values.spliterator();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void grow() {
		if (this.defaultValue instanceof ValueListImpl<?>) {
			this.values.add((T) ((ValueListImpl<?>) this.defaultValue).copy());
		} else if (this.defaultValue instanceof ValueMapImpl<?>) {
			this.values.add((T) ((ValueMapImpl<?>) this.defaultValue).copy());
		} else {
			this.values.add(this.defaultValue);
		}
	}
}
