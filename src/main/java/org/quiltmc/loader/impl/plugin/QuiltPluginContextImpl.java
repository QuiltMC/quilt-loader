package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModPlugin;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

public class QuiltPluginContextImpl extends BasePluginContext {

	final ModLoadOption optionFrom;
	final Path pluginPath;
	final QuiltPluginClassLoader classLoader;
	final QuiltLoaderPlugin plugin;

	public QuiltPluginContextImpl(//
		QuiltPluginManagerImpl manager, ModLoadOption from, Map<String, LoaderValue> previousData //
	) throws ReflectiveOperationException {

		super(manager, from.id());
		this.optionFrom = from;
		this.pluginPath = from.resourceRoot();

		ClassLoader parent = getClass().getClassLoader();
		ModPlugin pluginMeta = from.metadata().plugin();
		if (pluginMeta == null) {
			throw new IllegalArgumentException("No plugin metadata!");
		}
		classLoader = new QuiltPluginClassLoader(manager, parent, pluginPath, pluginMeta);

		Class<?> cls = classLoader.loadClass(pluginMeta.pluginClass());
		Object obj = cls.getDeclaredConstructor().newInstance();
		this.plugin = (QuiltLoaderPlugin) obj;

		plugin.load(this, previousData);
	}

	@Override
	public QuiltLoaderPlugin plugin() {
		return plugin;
	}

	@Override
	public Path pluginPath() {
		return pluginPath;
	}

	Map<String, LoaderValue> unload() {
		Map<String, LoaderValue> data = new HashMap<>();

		plugin.unload(data);

		// Just to ensure the resulting map is not empty
		data.put("quilt.plugin.reloaded", LoaderValueFactory.getFactory().bool(true));

		return data;
	}
}
