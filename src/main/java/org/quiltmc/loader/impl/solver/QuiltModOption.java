package org.quiltmc.loader.impl.solver;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

public class QuiltModOption extends ModLoadOption {

	final QuiltPluginManager pluginManager;
	final FullModMetadata metadata;
	final Path from, resourceRoot;

	public QuiltModOption(QuiltPluginManager pluginManager, FullModMetadata metadata, Path from, Path resourceRoot) {
		this.pluginManager = pluginManager;
		this.metadata = metadata;
		this.from = from;
		this.resourceRoot = resourceRoot;
	}

	@Override
	public FullModMetadata metadata() {
		return metadata;
	}

	@Override
	public Path from() {
		return from;
	}

	@Override
	public Path resourceRoot() {
		return resourceRoot;
	}

	@Override
	public String toString() {
		return "{QuiltModOption '" + metadata.id() + "' from " + pluginManager.describePath(from) + "}";
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_QUILT;
	}
}
