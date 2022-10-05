package org.quiltmc.loader.impl.plugin.gui;

import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;

final class PluginIconCustom implements PluginGuiIcon {

	final int index;

	public PluginIconCustom(int index) {
		this.index = index;
	}

	@Override
	public String tempToStatusNodeStr() {
		return "!" + index;
	}

	@Override
	public boolean isInLoader() {
		return false;
	}
}
