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

package org.quiltmc.loader.api.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.config.ConfigImpl;

@ApiStatus.NonExtendable
public interface Config {
	String getModId();

	String getId();

	/**
	 * The path this config will be saved in, relative to Quilt Loaders config directory.
	 *
	 * @return a save path
	 */
	Path getSavePath();

	/**
	 * Adds a listener to this {@link Config} that's called whenever any of its values is updated
	 *
	 * @param callback an update listener
	 */
	void register(UpdateCallback callback);

	Iterable<String> flags();

	boolean hasFlag(String flag);

	<M> Iterable<M> metadata(MetadataType<M> type);

	<M> boolean hasMetadata(MetadataType<M> type);

	Iterable<TrackedValue<?>> values();

	TrackedValue<?> getValue(Iterable<String> key);

	Iterable<ValueTreeNode> nodes();

	static Config create(String modId, String id, Path path, Creator... creators) {
		return ConfigImpl.create(modId, id, path, creators);
	}

	static Config create(String modId, String id, Creator... creators) {
		return ConfigImpl.create(modId, id, creators);
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, path, before, configCreatorClass, after);
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, path, before, configCreatorClass, builder -> {});
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, path, builder -> {}, configCreatorClass, after);
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, path, builder -> {}, configCreatorClass, builder -> {});
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Creator before, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, Paths.get(""), before, configCreatorClass, after);
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Creator before, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, Paths.get(""), before, configCreatorClass, builder -> {});
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, Paths.get(""), builder -> {}, configCreatorClass, after);
	}

	static <C> ConfigWrapper<C> create(String modId, String id, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, Paths.get(""), builder -> {}, configCreatorClass, builder -> {});
	}


	interface UpdateCallback {
		void onUpdate(Config config);
	}

	interface Creator {
		void create(Builder builder);
	}

	@ApiStatus.NonExtendable
	interface Builder {
		Builder field(TrackedValue<?> value);
		Builder section(String key, Consumer<SectionBuilder> creator);
		Builder flag(String flag);
		<M> Builder metadata(MetadataType<M> type, M value);
		Builder callback(UpdateCallback callback);
		Builder fileType(String fileType);
	}

	@ApiStatus.NonExtendable
	interface SectionBuilder {
		<T> SectionBuilder field(TrackedValue<T> value);
		SectionBuilder section(String key, Consumer<SectionBuilder> creator);
		SectionBuilder flag(String flag);
		<M> SectionBuilder metadata(MetadataType<M> type, M value);
	}
}
