package org.quiltmc.loader.impl.plugin.fabric;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

public class FabricModOption extends InternalModOptionBase {

	public FabricModOption(QuiltPluginContext pluginContext, FabricLoaderModMetadata meta, Path from, Path resourceRoot,
		boolean mandatory) {

		super(pluginContext, meta.asQuiltModMetadata(), from, resourceRoot, mandatory);
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_FABRIC;
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return new FabricModContainer(pluginContext.pluginId(), metadata, from, transformedResourceRoot);
	}
}
