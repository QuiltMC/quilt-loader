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

package org.quiltmc.loader.impl.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.TrackedValue;

public class TrackedValueBuilderImpl<T> implements TrackedValue.Builder<T> {
	private final T defaultValue;
	private final Set<String> key = new LinkedHashSet<>();
	private final Set<String> flags = new LinkedHashSet<>();
	private final Map<MetadataType<?>, List<?>> metadata = new LinkedHashMap<>();
	private final List<TrackedValue.UpdateCallback<T>> callbacks = new ArrayList<>();
	private final List<Constraint<T>> constraints = new ArrayList<>();

	public TrackedValueBuilderImpl(T defaultValue, String key0) {
		this.defaultValue = defaultValue;
		this.key.add(key0);
	}

	@Override
	public TrackedValue.Builder<T> key(String key) {
		this.key.add(key);

		return this;
	}

	@Override
	public TrackedValue.Builder<T> flag(String flag) {
		this.flags.add(flag);

		return this;
	}

	@Override
	public <M> TrackedValue.Builder<T> metadata(MetadataType<M> type, M value) {
		List<M> metadata;

		if (this.metadata.containsKey(type)) {
			//noinspection unchecked
			metadata = (List<M>) this.metadata.get(type);
		} else {
			metadata = new ArrayList<>();
			this.metadata.put(type, metadata);
		}

		metadata.add(value);

		return this;
	}

	@Override
	public TrackedValue.Builder<T> constraint(Constraint<T> constraint) {
		this.constraints.add(constraint);

		return this;
	}

	@Override
	public TrackedValue.Builder<T> callback(TrackedValue.UpdateCallback<T> callback) {
		this.callbacks.add(callback);

		return this;
	}

	public TrackedValue<T> build() {
		return new TrackedValueImpl<>(
				new ValueKeyImpl(this.key.toArray(new String[0])),
				this.defaultValue,
				this.flags,
				this.metadata,
				this.callbacks,
				this.constraints);
	}
}
