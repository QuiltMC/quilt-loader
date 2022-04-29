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
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.ConfigImpl;

@ApiStatus.NonExtendable
public interface Config {
	String getModId();

	String getId();

	/**
	 * The path this config will be saved in, relative to Quilt Loader's config directory.
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

	/**
	 * Returns all values held by this config file
	 *
	 * <p>For all nodes, including section nodes, see {@link #nodes}
	 *
	 * @return all values held by this config file
	 */
	Iterable<TrackedValue<?>> values();

	/**
	 * @param key an iterable of key components to access
	 * @return the value contained by this config class with key components
	 */
	TrackedValue<?> getValue(Iterable<String> key);

	/**
	 * @return all nodes of the value tree represented by this config file, including section nodes
	 */
	Iterable<ValueTreeNode> nodes();

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.json5"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.json5"
	 * @param creators any number of {@link Creator}s that can be used to configure the resulting config
	 */
	static Config create(String modId, String id, Path path, Creator... creators) {
		return ConfigImpl.create(modId, id, path, creators);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the configs id
	 * @param creators any number of {@link Creator}s that can be used to configure the resulting config
	 */
	static Config create(String modId, String id, Creator... creators) {
		return ConfigImpl.create(modId, id, creators);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.json5"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.json5"
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, path, before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.json5"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.json5"
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Creator before, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, path, before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.json5"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.json5"
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, path, builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.json5"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.json5"
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Path path, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, path, builder -> {}, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Creator before, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, Paths.get(""), before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Creator before, Class<C> configCreatorClass) {
		return ConfigImpl.create(modId, id, Paths.get(""), before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String modId, String id, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(modId, id, Paths.get(""), builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, or String)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param modId the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
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
		/**
		 * Adds a value to this config file
		 *
		 * @return this
		 */
		Builder field(TrackedValue<?> value);

		/**
		 * Creates a new section nested within this config file
		 *
		 * @return this
		 */
		Builder section(String key, Consumer<SectionBuilder> creator);

		/**
		 * Adds a unique flag to this sections metadata
		 *
		 * @param flag a string value flag to track
		 * @return this
		 */
		Builder flag(String flag);

		/**
		 * Adds a piece of metadata to this section
		 *
		 * A {@link SectionBuilder} can have any number of values associated with each {@link MetadataType}.
		 *
		 * @param type the type of this metadata
		 * @param value a value to append to the resulting {@link SectionBuilder}'s metadata
		 * @return this
		 */
		<M> Builder metadata(MetadataType<M> type, M value);

		/**
		 * Adds a default listener to the resulting {@link Config} that's called whenever any of its values updated
		 *
		 * @param callback an update listener
		 * @return this
		 */
		Builder callback(UpdateCallback callback);

		/**
		 * Sets the default file type for the config file this config will be saved to
		 *
		 * Note that this can be overridden by the end user with a launch parameter
		 *
		 * @return this
		 */
		Builder fileType(String fileType);
	}

	@ApiStatus.NonExtendable
	interface SectionBuilder {
		/**
		 * Adds a field to this config section
		 */
		<T> SectionBuilder field(TrackedValue<T> value);

		/**
		 * Creates a new section nested within this one
		 */
		SectionBuilder section(String key, Consumer<SectionBuilder> creator);

		/**
		 * Adds a unique flag to this sections metadata
		 *
		 * @param flag a string value flag to track
		 * @return this
		 */
		SectionBuilder flag(String flag);

		/**
		 * Adds a piece of metadata to this section
		 *
		 * A {@link SectionBuilder} can have any number of values associated with each {@link MetadataType}.
		 *
		 * @param type the type of this metadata
		 * @param value a value to append to the resulting {@link SectionBuilder}'s metadata
		 * @return this
		 */
		<M> SectionBuilder metadata(MetadataType<M> type, M value);
	}
}
