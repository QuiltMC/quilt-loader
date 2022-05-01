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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.ConfigWrapper;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.Serializer;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.builders.ConfigBuilderImpl;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;
import org.quiltmc.loader.impl.config.tree.Trie;
import org.quiltmc.loader.impl.config.util.ConfigSerializers;
import org.quiltmc.loader.impl.config.builders.ReflectiveConfigCreatorImpl;
import org.quiltmc.loader.impl.config.util.ConfigsImpl;
import org.quiltmc.loader.impl.util.ImmutableIterable;

public final class ConfigImpl extends AbstractMetadataContainer implements Config {
	private final String modId, id;
	private final Path path;
	private final List<Config.UpdateCallback> callbacks;
	private final Trie values;
	private final String defaultFileType;

	public ConfigImpl(String modId, String id, Path path, Set<String> flags, Map<MetadataType<?>, List<?>> metadata, List<UpdateCallback> callbacks, Trie values, String defaultFileType) {
		super(flags, metadata);
		this.modId = modId;
		this.id = id;
		this.path = path;
		this.callbacks = callbacks;
		this.values = values;
		this.defaultFileType = defaultFileType;
	}

	@Override
	public String getModId() {
		return this.modId;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Path getSavePath() {
		return this.path;
	}

	@Override
	public void register(UpdateCallback callback) {
		this.callbacks.add(callback);
	}

	@Override
	public boolean hasFlag(String flag) {
		return this.flags.contains(flag);
	}

	@Override
	public <M> boolean hasMetadata(MetadataType<M> type) {
		return this.metadata.containsKey(type) && !this.metadata.get(type).isEmpty();
	}

	public String getDefaultFileType() {
		return this.defaultFileType;
	}

	public Path getPath() {
		return QuiltLoader.getConfigDir().resolve(this.modId).resolve(this.path).resolve(this.id + "." + ConfigSerializers.getSerializer(this.defaultFileType).getFileExtension());
	}

	public void serialize() {
		Path path = this.getPath();
		try {
			Files.createDirectories(path.getParent());
			ConfigSerializers.getSerializer(this.defaultFileType).serialize(this, Files.newOutputStream(path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void invokeCallbacks() {
		for (Config.UpdateCallback callback : this.callbacks) {
			callback.onUpdate(this);
		}
	}

	public Iterable<TrackedValue<?>> values() {
		return new Iterable<TrackedValue<?>>() {
			@NotNull
			@Override
			public Iterator<TrackedValue<?>> iterator() {
				return new Iterator<TrackedValue<?>>() {
					private final Iterator<ValueTreeNode> itr = ConfigImpl.this.values.leaves().iterator();
					private ValueTreeNode next;

					@Override
					public boolean hasNext() {
						// Consume non-leaf nodes
						while (this.itr.hasNext() && this.next == null) {
							this.next = this.itr.next();
						}

						return next != null;
					}

					@Override
					public TrackedValue<?> next() {
						TrackedValue<?> value = (TrackedValue<?>) this.next;

						this.next = null;

						return value;
					}
				};
			}
		};
	}

	@Override
	public TrackedValue<?> getValue(Iterable<String> key) {
		return this.values.get(key);
	}

	public Iterable<ValueTreeNode> nodes() {
		return new ImmutableIterable<>(this.values.nodes());
	}

	public static Config create(String modId, String id, Creator... creators) {
		return create(modId, id, Paths.get(""), creators);
	}

	public static Config create(String modId, String id, Path path, Creator... creators) {
		ConfigBuilderImpl builder = new ConfigBuilderImpl(modId, id, path);

		for (Creator creator : creators) {
			creator.create(builder);
		}

		ConfigImpl config = builder.build();

		ConfigsImpl.put(modId, config);

		for (TrackedValue<?> value : config.values()) {
			((TrackedValueImpl<?>) value).setConfig(config);
		}

		doInitialSerialization(config);

		return config;
	}

	public static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass, Creator after) {
		ConfigBuilderImpl builder = new ConfigBuilderImpl(modId, id, path);

		before.create(builder);
		C c = ReflectiveConfigCreatorImpl.of(configCreatorClass).create(builder);
		after.create(builder);

		ConfigImpl config = builder.build();

		ConfigsImpl.put(modId, config);

		for (TrackedValue<?> value : config.values()) {
			((TrackedValueImpl<?>) value).setConfig(config);
		}

		doInitialSerialization(config);

		return new ConfigWrapper<C>() {
			@Override
			public Config getConfig() {
				return config;
			}

			@Override
			public C getWrapped() {
				return c;
			}
		};
	}

	private static void doInitialSerialization(ConfigImpl config) {
		Serializer defaultSerializer = ConfigSerializers.getActualSerializer(config.getDefaultFileType());
		Serializer serializer = ConfigSerializers.getSerializer(config.getDefaultFileType());

		Path directory = QuiltLoader.getConfigDir().resolve(config.getModId()).resolve(config.getSavePath());
		Path defaultPath = directory.resolve(config.getId() + "." + defaultSerializer.getFileExtension());
		Path path = directory.resolve(config.getId() + "." + serializer.getFileExtension());

		try {
			if ((defaultSerializer == serializer || !Files.exists(defaultPath)) && Files.exists(path)) {
				serializer.deserialize(config, Files.newInputStream(path));
			} else if (Files.exists(defaultPath)) {
				defaultSerializer.deserialize(config, Files.newInputStream(defaultPath));

				try {
					Files.delete(defaultPath);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			serializer.serialize(config, Files.newOutputStream(path));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
