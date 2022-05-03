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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.impl.config.CompoundConfigValueImpl;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;

public final class ValueMapImpl<T> implements ValueMap<T>, CompoundConfigValueImpl<T, ValueMap<T>> {
	private final T defaultValue;
	private final Map<String, T> values;

	private TrackedValueImpl<?> configValue;

	public ValueMapImpl(T defaultValue, Map<String, T> values) {
		this.defaultValue = defaultValue;
		this.values = values;
	}

	public void setValue(TrackedValueImpl<?> configValue) {
		this.configValue = configValue;

		if (this.defaultValue instanceof CompoundConfigValueImpl<?, ?>) {
			for (T value : this.values.values()) {
				((CompoundConfigValueImpl<? ,?>) value).setValue(configValue);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public ValueMap<T> copy() {
		Map<String, T> values = new LinkedHashMap<>();

		for (Map.Entry<String, T> entry : this) {
			T value = entry.getValue();

			if (value instanceof ValueListImpl<?>) {
				values.put(entry.getKey(), (T) ((ValueListImpl<?>) value).copy());
			} else if (value instanceof ValueMapImpl<?>) {
				values.put(entry.getKey(), (T) ((ValueMapImpl<?>) value).copy());
			} else {
				values.put(entry.getKey(), value);
			}
		}

		ValueMapImpl<T> result = new ValueMapImpl<>(this.defaultValue, values);

		result.setValue(this.configValue);

		return result;
	}

	@Override
	public int size() {
		return this.values.size();
	}

	@Override
	public boolean isEmpty() {
		return this.values.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return this.values.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return this.values.containsValue(value);
	}

	@Override
	public T get(Object key) {
		return this.values.get(key);
	}

	@Nullable
	@Override
	public T put(String key, T value) {
		T v = this.values.put(key, value);

		if (value instanceof CompoundConfigValueImpl<?, ?>) {
			((CompoundConfigValueImpl<?, ?>) value).setValue(this.configValue);
		}

		this.configValue.update();

		return v;
	}

	@Override
	public T remove(Object key) {
		T result = this.values.remove(key);

		this.configValue.update();

		return result;
	}

	@Override
	public void putAll(@NotNull Map<? extends String, ? extends T> m) {
		this.values.putAll(m);

		for (T value : m.values()) {
			if (value instanceof CompoundConfigValueImpl<?, ?>) {
				((CompoundConfigValueImpl<?, ?>) value).setValue(this.configValue);
			}
		}

		this.configValue.update();
	}

	@Override
	public void clear() {
		this.values.clear();
		this.configValue.update();
	}

	@NotNull
	@Override
	public Set<String> keySet() {
		return this.values.keySet();
	}

	@NotNull
	@Override
	public Collection<T> values() {
		return this.values.values();
	}

	@NotNull
	@Override
	public Set<Map.Entry<String, T>> entrySet() {
		return this.values.entrySet();
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
	@SuppressWarnings("unchecked")
	public void grow() {
		if (this.defaultValue instanceof ValueListImpl<?>) {
			this.values.put("", (T) ((ValueListImpl<?>) this.defaultValue).copy());
		} else if (this.defaultValue instanceof ValueMapImpl<?>) {
			this.values.put("", (T) ((ValueMapImpl<?>) this.defaultValue).copy());
		} else {
			this.values.put("", this.defaultValue);
		}
	}

	@NotNull
	@Override
	public Iterator<Map.Entry<String, T>> iterator() {
		return this.values.entrySet().iterator();
	}
}
