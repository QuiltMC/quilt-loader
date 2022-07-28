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

import org.quiltmc.config.api.Config;
import org.quiltmc.config.api.WrappedConfig;
import org.quiltmc.config.api.annotations.ConfigFieldAnnotationProcessor;
import org.quiltmc.config.api.annotations.ConfigFieldAnnotationProcessors;
import org.quiltmc.config.api.values.ValueList;
import org.quiltmc.config.api.values.ValueMap;
import org.quiltmc.config.impl.ConfigImpl;
import org.quiltmc.loader.impl.config.QuiltConfigImpl;

/**
 * Class containing helper methods that mods should use to create config files as part of Quilt's config system.
 */
public final class QuiltConfig {
	/**
	 * Creates and registers a config file
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param creators any number of {@link Config.Creator}s that can be used to configure the resulting config
	 */
	public static Config create(String family, String id, Path path, Config.Creator... creators) {
		return ConfigImpl.create(QuiltConfigImpl.getConfigEnvironment(), family, id, path, creators);
	}

	/**
	 * Creates and registers a config file
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param creators any number of {@link Config.Creator}s that can be used to configure the resulting config
	 */
	public static Config create(String family, String id, Config.Creator... creators) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
		return Config.create(QuiltConfigImpl.getConfigEnvironment(), family, id, path, before, configCreatorClass, after);
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Path path, Class<C> configCreatorClass, Config.Creator after) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Path path, Class<C> configCreatorClass) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass) {
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
	 *         <li>A complex type (a {@link ValueList} or {@link ValueMap} of basic or complex types or a {@link org.quiltmc.config.api.values.ConfigSerializableObject})</li>
	 *         <li>An object whose class follows these rules</li>
	 *     </ul>
	 * </ul>
	 *
	 * <p>Certain annotations can also be used on fields of this class to attach metadata to them. The {@link org.quiltmc.config.api.annotations.Comment}
	 * annotation is one such annotation that is provided by default, but additional {@link ConfigFieldAnnotationProcessor}s
	 * can be registered with {@link ConfigFieldAnnotationProcessors#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Class<C> configCreatorClass, Config.Creator after) {
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
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends WrappedConfig> C create(String family, String id, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, builder -> {});
	}
	
	private QuiltConfig() {
	}
}
