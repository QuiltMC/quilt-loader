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

package org.quiltmc.loader.impl.config.builders;

import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ValueKey;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SectionBuilderImpl implements Config.SectionBuilder {
	private final ValueKey key;
	private final ConfigBuilderImpl builder;
	final Set<String> flags = new LinkedHashSet<>();
	final Map<MetadataType<?, ?>, MetadataType.Builder<?>> metadata = new LinkedHashMap<>();

	public SectionBuilderImpl(ValueKey key, ConfigBuilderImpl builder) {
		this.key = key;
		this.builder = builder;
	}

	@Override
	public Config.SectionBuilder field(TrackedValue<?> value) {
		ValueKey key = this.key.child(value.getKey());
		this.builder.values.put(key, ((TrackedValueImpl<?>) value).setKey(key));

		return this;
	}

	@Override
	public Config.SectionBuilder section(String key, Consumer<Config.SectionBuilder> creator) {
		ValueKey valueKey = this.key.child(key);
		SectionBuilderImpl sectionBuilder = new SectionBuilderImpl(valueKey, this.builder);

		this.builder.values.put(valueKey, sectionBuilder);
		creator.accept(sectionBuilder);

		return this;
	}

	@Override
	public Config.SectionBuilder flag(String flag) {
		this.flags.add(flag);

		return this;
	}

	@SuppressWarnings("unchecked")
	public <M, B extends MetadataType.Builder<M>> Config.SectionBuilder metadata(MetadataType<M, B> type, Consumer<B> builderConsumer) {
		builderConsumer.accept((B) this.metadata.computeIfAbsent(type, t -> type.newBuilder()));

		return this;
	}

	public Map<MetadataType<?, ?>, Object> buildMetadata() {
		Map<MetadataType<?, ?>, Object> metadata = new LinkedHashMap<>();

		for (Map.Entry<MetadataType<?, ?>, MetadataType.Builder<?>> entry : this.metadata.entrySet()) {
			metadata.put(entry.getKey(), entry.getValue().build());
		}

		return metadata;
	}
}
