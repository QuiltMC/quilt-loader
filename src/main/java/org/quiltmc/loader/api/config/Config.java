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
import org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor;
import org.quiltmc.loader.api.config.values.ValueKey;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.ConfigImpl;

@ApiStatus.NonExtendable
public interface Config {
	/**
	 * The ID of the config family this config file belongs to, also the folder the resulting file will be saved in.
	 */
	String family();

	/**
	 * The unique ID of this config file, also the name of the resulting file.
	 */
	String id();

	/**
	 * The path this config will be saved in, relative to the root config directory.
	 *
	 * @return a save path
	 */
	Path savePath();

	/**
	 * Adds a listener to this {@link Config} that's called whenever any of its values are updated
	 *
	 * @param callback an update listener
	 */
	void registerCallback(UpdateCallback callback);

	/**
	 * @return the metadata attached to this value for the specified type
	 */
	<M> M metadata(MetadataType<M, ?> type);

	/**
	 * @return whether or not this value has any metadata of the specified type
	 */
	<M> boolean hasMetadata(MetadataType<M, ?> type);

	/**
	 * Serialize this config and all its values to disk
	 */
	void save();

	/**
	 * Returns all values held by this config file
	 *
	 * <p>For all nodes, including section nodes, see {@link #nodes}
	 *
	 * @return all values held by this config file
	 */
	Iterable<TrackedValue<?>> values();

	/**
	 * @param key an iterable of key components that make up a {@link TrackedValue}'s {@link ValueKey}
	 * @return the value contained by this config class
	 */
	TrackedValue<?> getValue(Iterable<String> key);

	/**
	 * @return all nodes of the value tree represented by this config file, including section nodes
	 */
	Iterable<ValueTreeNode> nodes();

	/**
	 * Creates and registers a config file
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param creators any number of {@link Creator}s that can be used to configure the resulting config
	 */
	static Config create(String family, String id, Path path, Creator... creators) {
		return ConfigImpl.create(family, id, path, creators);
	}

	/**
	 * Creates and registers a config file
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param creators any number of {@link Creator}s that can be used to configure the resulting config
	 */
	static Config create(String family, String id, Creator... creators) {
		return create(family, id, Paths.get(""), creators);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Path path, Creator before, Class<C> configCreatorClass, Creator after) {
		return ConfigImpl.create(family, id, path, before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Path path, Creator before, Class<C> configCreatorClass) {
		return create(family, id, path, before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Path path, Class<C> configCreatorClass, Creator after) {
		return create(family, id, path, builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Path path, Class<C> configCreatorClass) {
		return create(family, id, path, builder -> {}, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Creator before, Class<C> configCreatorClass, Creator after) {
		return create(family, id, Paths.get(""), before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Creator before, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static non-transient field should be final, not null, and be one of the following types:</li>
	 *     <ul>
	 *         <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.loader.api.config.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 * 
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.loader.api.config.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Creator} that can be used to configure the resulting config further
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Class<C> configCreatorClass, Creator after) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be final and be one of the following types:</li>
	 *     <ul>
	 *     	   <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
	 *     	   <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @return a {@link ConfigWrapper<C>}
	 */
	static <C> ConfigWrapper<C> create(String family, String id, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, builder -> {});
	}

	interface UpdateCallback {
		void onUpdate(Config config);
	}

	interface Creator {
		void create(Builder builder);
	}

	/**
	 * Marker interface to designate a class as a section for reflective configs
	 */
	interface Section {

	}

	@ApiStatus.NonExtendable
	interface Builder extends SectionBuilder {
		/**
		 * Adds a value to this config file
		 *
		 * A field should be either:
		 * <ul>
		 *     <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
		 *     <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
		 * </ul>
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
		 * Create or configure a piece of metadata
		 *
		 * @param type the type of metadata to configure
		 * @param builderConsumer the modifications to be made to the piece of metadata
		 * @return this
		 */
		<M, B extends MetadataType.Builder<M>> Builder metadata(MetadataType<M, B> type, Consumer<B> builderConsumer);

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
		Builder format(String format);
	}

	@ApiStatus.NonExtendable
	interface SectionBuilder extends MetadataContainerBuilder<SectionBuilder> {
		/**
		 * Adds a value to this config file
		 *
		 * A field should be either:
		 * <ul>
		 *     <li>A basic type (int, long, float, double, boolean, String, or enum)</li>
		 *     <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types)</li>
		 * </ul>
		 * @return this
		 */
		SectionBuilder field(TrackedValue<?> value);

		/**
		 * Creates a new section nested within this one
		 */
		SectionBuilder section(String key, Consumer<SectionBuilder> creator);

		/**
		 * Create or configure a piece of metadata
		 *
		 * @param type the type of metadata to configure
		 * @param builderConsumer the modifications to be made to the piece of metadata
		 * @return this
		 */
		<M, B extends MetadataType.Builder<M>> Config.SectionBuilder metadata(MetadataType<M, B> type, Consumer<B> builderConsumer);
	}
}
