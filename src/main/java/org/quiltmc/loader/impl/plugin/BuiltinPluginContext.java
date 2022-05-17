package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;

class BuiltinPluginContext extends BasePluginContext {

	final QuiltLoaderPlugin plugin;

	public BuiltinPluginContext(QuiltPluginManagerImpl manager, String pluginId, QuiltLoaderPlugin plugin) {
		super(manager, pluginId);
		this.plugin = plugin;
	}

	@Override
	public Path pluginPath() {
		throw new UnsupportedOperationException("Builtin plugins don't support pluginPath()");
	}

	@Override
	public QuiltLoaderPlugin plugin() {
		return plugin;
	}
}
