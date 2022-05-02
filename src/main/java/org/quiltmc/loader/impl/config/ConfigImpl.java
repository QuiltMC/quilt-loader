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
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.builders.ConfigBuilderImpl;
import org.quiltmc.loader.impl.config.builders.ReflectiveConfigCreator;
import org.quiltmc.loader.impl.config.tree.Trie;
import org.quiltmc.loader.impl.config.util.ConfigSerializers;
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
	public void registerCallback(UpdateCallback callback) {
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

		return builder.build();
	}

	public static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass, Creator after) {
		ReflectiveConfigCreator<C> creator = ReflectiveConfigCreator.of(configCreatorClass);
		Config config = create(modId, id, path, before, creator, after);
		C c = creator.getInstance();

		return new ConfigWrapper<C>() {
			@Override
			public C getWrapped() {
				return c;
			}

			@Override
			public Config getConfig() {
				return config;
			}
		};
	}
}
