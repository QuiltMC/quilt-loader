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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.Serializer;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.exceptions.ConfigParseException;
import org.quiltmc.loader.api.config.values.ValueKey;
import org.quiltmc.loader.impl.config.ConfigImpl;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;
import org.quiltmc.loader.impl.config.tree.Trie;
import org.quiltmc.loader.api.config.ConfigSerializers;
import org.quiltmc.loader.impl.config.util.ConfigsImpl;
import org.quiltmc.loader.impl.config.values.ValueKeyImpl;
import org.quiltmc.loader.impl.util.SystemProperties;

public final class ConfigBuilderImpl implements Config.Builder {
	private final String familyId, id;
	private final Path path;

	private final Map<MetadataType<?, ?>, MetadataType.Builder<?>> metadata = new LinkedHashMap<>();
	private final List<Config.UpdateCallback> callbacks = new ArrayList<>();

	final Trie values = new Trie();

	private String format = System.getProperty(SystemProperties.DEFAULT_CONFIG_EXTENSION, "toml");

	public ConfigBuilderImpl(String familyId, String id, Path path) {
		this.familyId = familyId;
		this.id = id;
		this.path = path;
	}

	@Override
	public Config.Builder field(TrackedValue<?> value) {
		this.values.put(value.key(), value);

		return this;
	}

	@Override
	public Config.Builder section(String key, Consumer<Config.SectionBuilder> creator) {
		ValueKey valueKey = new ValueKeyImpl(key);
		SectionBuilderImpl sectionBuilder = new SectionBuilderImpl(valueKey, this);

		creator.accept(sectionBuilder);
		this.values.put(valueKey, sectionBuilder);

		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <M, B extends MetadataType.Builder<M>> Config.Builder metadata(MetadataType<M, B> type, Consumer<B> builderConsumer) {
		builderConsumer.accept((B) this.metadata.computeIfAbsent(type, t -> type.newBuilder()));

		return this;
	}

	@Override
	public Config.Builder callback(Config.UpdateCallback callback) {
		this.callbacks.add(callback);

		return this;
	}

	@Override
	public Config.Builder format(String format) {
		this.format = format;

		return this;
	}

	public ConfigImpl build() {
		Map<MetadataType<?, ?>, Object> metadata = new LinkedHashMap<>();

		for (Map.Entry<MetadataType<?, ?>, MetadataType.Builder<?>> entry : this.metadata.entrySet()) {
			metadata.put(entry.getKey(), entry.getValue().build());
		}

		ConfigImpl config = new ConfigImpl(this.familyId, this.id, this.path, metadata, this.callbacks, this.values, this.format);

		ConfigsImpl.put(familyId, config);

		for (TrackedValue<?> value : config.values()) {
			((TrackedValueImpl<?>) value).setConfig(config);
		}

		doInitialSerialization(config);

		return config;
	}

	public static void doInitialSerialization(ConfigImpl config) {
		Serializer defaultSerializer = ConfigSerializers.getActualSerializer(config.getDefaultFileType());
		Serializer serializer = ConfigSerializers.getSerializer(config.getDefaultFileType());

		Path directory = QuiltLoader.getConfigDir().resolve(config.family()).resolve(config.savePath());
		Path defaultPath = directory.resolve(config.id() + "." + defaultSerializer.getFileExtension());
		Path path = directory.resolve(config.id() + "." + serializer.getFileExtension());

		try {
			Files.createDirectories(path.getParent());

			if ((defaultSerializer == serializer || !Files.exists(defaultPath)) && Files.exists(path)) {
				serializer.deserialize(config, Files.newInputStream(path));
			} else if (Files.exists(defaultPath)) {
				defaultSerializer.deserialize(config, Files.newInputStream(defaultPath));

				try {
					Files.delete(defaultPath);
				} catch (IOException e) {
					throw new ConfigParseException(e);
				}
			}

			serializer.serialize(config, Files.newOutputStream(path));
		} catch (IOException e) {
			throw new ConfigParseException(e);
		}
	}
}