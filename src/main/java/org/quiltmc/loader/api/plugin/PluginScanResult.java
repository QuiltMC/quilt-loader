package org.quiltmc.loader.api.plugin;

import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;

/** Used as a result from {@link QuiltLoaderPlugin#scanZip(java.nio.file.Path, boolean, PluginGuiTreeNode)} and
 * {@link QuiltLoaderPlugin#scanUnknownFile(java.nio.file.Path, boolean, PluginGuiTreeNode)}. */
public enum PluginScanResult {
	/** Indicates that the plugin didn't find anything useful. */
	IGNORED,

	/** Indicates that the plugin has loaded the file as a mod. */
	FOUND;
}
