package org.quiltmc.loader.api.plugin.solver;

import java.nio.file.Path;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;

public abstract class ModLoadOption extends LoadOption {

	public abstract FullModMetadata metadata();

	/** @return The {@link Path} where this is loaded from. This should be either the Path that was passed to
	 *         {@link QuiltLoaderPlugin#scanZip(Path, PluginGuiTreeNode)} or the Path that was passed to
	 *         {@link QuiltLoaderPlugin#scanUnknownFile(Path, PluginGuiTreeNode)}. */
	public abstract Path from();

	/** @return The {@link Path} where this mod's classes and resources can be loaded from. */
	public abstract Path resourceRoot();

	// TODO: How do we turn this into a ModContainer?
	// like... how should we handle mods that need remapping vs those that don't?
	// plus how is that meant to work with caches in the future?

	public final String group() {
		return metadata().group();
	}

	public final String id() {
		return metadata().id();
	}

	public final Version version() {
		return metadata().version();
	}

	public abstract PluginGuiIcon modTypeIcon();
}
