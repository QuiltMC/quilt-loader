package org.quiltmc.loader.impl.plugin.quilt;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

public class BuiltinModOption extends InternalModOptionBase {

	public BuiltinModOption(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from, Path resourceRoot) {
		super(pluginContext, meta, from, resourceRoot, true, false);
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_JAVA_PACKAGE;
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		if (!transformedResourceRoot.equals(resourceRoot)) {
			try {
				resourceRoot.getFileSystem().close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return new BuiltinModContainer(pluginContext.pluginId(), metadata, from, transformedResourceRoot);
	}
}
