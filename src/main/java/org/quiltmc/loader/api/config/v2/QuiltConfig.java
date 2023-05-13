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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
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
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
		return ConfigFactory.create(QuiltConfigImpl.getConfigEnvironment(), family, id, path, before, configCreatorClass, after);
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
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
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Config.Creator before, Class<C> configCreatorClass) {
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
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
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Class<C> configCreatorClass, Config.Creator after) {
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param path additional path elements to include as part of this configs file, e.g.
	 *             if the path is empty, the config file might be ".minecraft/config/example_mod/id.toml"
	 *             if the path is "client/gui", the config file might be ".minecraft/config/example_mod/client/gui/id.toml"
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Path path, Class<C> configCreatorClass) {
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass, Config.Creator after) {
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param before a {@link Config.Creator} that can be used to configure the resulting config further
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Config.Creator before, Class<C> configCreatorClass) {
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
	 * can be registered with {@link ConfigFieldAnnotationProcessor#register(Class, ConfigFieldAnnotationProcessor)}.
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @param after a {@link Config.Creator} that can be used to configure the resulting config further
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Class<C> configCreatorClass, Config.Creator after) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, after);
	}

	/**
	 * Creates and registers a config with fields derived from the fields of the passed class
	 *
	 * <p>The passed class should have the following characteristics:
	 * <ul>
	 *     <li>Has a public no-argument constructor</li>
	 *     <li>Each non-static field should be a final field initialized from the helper methods in{@link ReflectiveConfig}</li>
	 * </ul>
	 *
	 * @param family the mod owning the resulting config file
	 * @param id the config's id
	 * @param configCreatorClass a class as described above
	 * @return a {@link WrappedConfig <C>}
	 */
	public static <C extends ReflectiveConfig> C create(String family, String id, Class<C> configCreatorClass) {
		return create(family, id, Paths.get(""), builder -> {}, configCreatorClass, builder -> {});
	}

	private QuiltConfig() {
	}
}
