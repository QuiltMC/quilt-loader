/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.api.config.v2;

import org.quiltmc.config.api.Config;
import org.quiltmc.config.api.ReflectiveConfig;
import org.quiltmc.config.impl.ConfigImpl;
import org.quiltmc.config.implementor_api.ConfigFactory;
import org.quiltmc.loader.impl.config.QuiltConfigImpl;

import java.nio.file.Path;
import java.nio.file.Paths;

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
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 * 
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
		return ConfigFactory.create(QuiltConfigImpl.getConfigEnvironment(), family, id, path, before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass) {
		return create(family, id, path, before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the configs id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Class<C> configCreatorClass, Config.Creator after) {
		return create(family, id, path, builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Class<C> configCreatorClass) {
		return create(family, id, path, builder -> {}, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
		return create(family, id, Paths.get(""), before, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), before, configCreatorClass, builder -> {});
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Class<C> configCreatorClass, Config.Creator after) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with a class that contains its WrappedValues as fields.
	 * 
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @return a {@link ReflectiveConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, builder -> {});
	}

	private QuiltConfig() {
	}
}
