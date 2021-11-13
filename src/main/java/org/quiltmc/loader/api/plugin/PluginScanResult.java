package org.quiltmc.loader.api.plugin;

/** Used as a result from {@link QuiltLoaderPlugin#scanZip(java.nio.file.Path)} and
 * {@link QuiltLoaderPlugin#scanUnknownFile(java.nio.file.Path)}. */
public enum PluginScanResult {
	/** Indicates that the plugin didn't find anything useful. */
	IGNORED,

	/** Indicates that the plugin has loaded the file as a mod. */
	FOUND;
}
