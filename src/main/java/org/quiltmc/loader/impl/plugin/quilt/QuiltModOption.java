package org.quiltmc.loader.impl.plugin.quilt;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

public class QuiltModOption extends InternalModOptionBase {

	public QuiltModOption(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from, Path resourceRoot,
		boolean mandatory, boolean requiresRemap) {
		super(pluginContext, meta, from, resourceRoot, mandatory, requiresRemap);
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_QUILT;
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return new QuiltModContainer(pluginContext, metadata, from, transformedResourceRoot);
	}

}
