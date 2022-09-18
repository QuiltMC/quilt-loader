package org.quiltmc.loader.impl.plugin;

import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.impl.discovery.ModResolutionException;

/** Indicates that a plugin has added a node with a {@link WarningLevel} of {@link WarningLevel#ERROR} to the tree, so
 * the current tree should be shown as-is. */
final class TreeContainsModError extends ModResolutionException {

}
