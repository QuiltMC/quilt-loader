package org.quiltmc.loader.impl.plugin.gui;

import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;

final class PluginIconBuiltin implements PluginGuiIcon {
	final String path;

	public PluginIconBuiltin(String path) {
		this.path = path;
	}

	@Override
	public String tempToStatusNodeStr() {
		return path;
	}

	@Override
	public boolean isInLoader() {
		return true;
	}
}
