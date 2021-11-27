package org.quiltmc.loader.impl.plugin;

import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;

public abstract class BuiltinQuiltPlugin implements QuiltLoaderPlugin {
	private QuiltPluginContext context;

	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData) {
		this.context = context;
	}

	@Override
	public void unload(Map<String, LoaderValue> data) {
		throw new UnsupportedOperationException("Builtin plugins cannot be unloaded!");
	}

	public QuiltPluginContext context() {
		return context;
	}
}
