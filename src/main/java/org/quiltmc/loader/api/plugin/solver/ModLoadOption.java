/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.api.plugin.solver;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModLoadType;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A special type of {@link LoadOption} that represents a mod. */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public abstract class ModLoadOption extends LoadOption {

	/** @return The plugin context for the plugin that loaded this mod. */
	public abstract QuiltPluginContext loader();

	/** @return The metadata that this mod either (a) is, or (b) points to, if this is an {@link AliasedLoadOption}. */
	public abstract ModMetadataExt metadata();

	/** @return The {@link Path} where this is loaded from. This should be either the Path that was passed to
	 *         {@link QuiltLoaderPlugin#scanZip(Path, boolean, PluginGuiTreeNode)} or the Path that was passed to
	 *         {@link QuiltLoaderPlugin#scanUnknownFile(Path, boolean, PluginGuiTreeNode)}. */
	public abstract Path from();

	/** @return The {@link Path} where this mod's classes and resources can be loaded from. */
	public abstract Path resourceRoot();

	/** @return True if this mod MUST be loaded or false if this should be loaded depending on it's {@link ModLoadType}.
	 *         Quilt returns true here for mods on the classpath and directly in the mods folder, but not when
	 *         jar-in-jar'd. */
	public abstract boolean isMandatory();

	// TODO: How do we turn this into a ModContainer?
	// like... how should we handle mods that need remapping vs those that don't?
	// plus how is that meant to work with caches in the future?

	/** @return The namespace to map classes from, or null if this mod shouldn't have it's classes remapped. */
	@Nullable
	public abstract String namespaceMappingFrom();

	public abstract boolean needsChasmTransforming();

	/** @return A hash of the origin files used for the mod. This is used to cache class transformations (like remapping
	 *         and chasm) between launches. This may be called off-thread. */
	public abstract byte[] computeOriginHash() throws IOException;

	/** @return The group for this mod. Normally this will just be the same as the group in {@link #metadata()}, but for
	 *         provided mods this may be different. */
	public String group() {
		return metadata().group();
	}

	/** @return The id for this mod. Normally this will just be the same as the id in {@link #metadata()}, but for
	 *         provided mods this may be different. */
	public String id() {
		return metadata().id();
	}

	/** @return The version for this mod. Normally this will just be the same as the version in {@link #metadata()}, but
	 *         for provided mods this may be different. */
	public Version version() {
		return metadata().version();
	}

	public abstract PluginGuiIcon modFileIcon();

	public abstract PluginGuiIcon modTypeIcon();

	public PluginGuiIcon modCompleteIcon() {
		return modFileIcon().withDecoration(modTypeIcon());
	}

	/** Populates the given gui node with information about this mod. The {@link PluginGuiTreeNode#text()} will have
	 * been set to the {@link #version()} before this method is called. */
	public void populateModsTabInfo(PluginGuiTreeNode guiNode) {
		guiNode.mainIcon(modTypeIcon());
		guiNode.addChild(QuiltLoaderText.of(loader().manager().describePath(from())))//
			.mainIcon(guiNode.manager().iconFolder());
	}

	public abstract ModContainerExt convertToMod(Path transformedResourceRoot);

	@Override
	public String toString() {
		return shortString();
	}

	/** Older temporary method for error descriptions */
	@Deprecated
	public abstract String shortString();

	/** Older temporary method for error descriptions */
	@Deprecated
	public String fullString() {
		return shortString() + " " + getSpecificInfo();
	}

	/** Older temporary method for error descriptions */
	@Deprecated
	public String getLoadSource() {
		return loader().manager().describePath(from());
	}

	/** Older temporary method for error descriptions */
	@Deprecated
	public abstract String getSpecificInfo();

	public boolean couldResourcesChange() {
		Path from = from();
		if (from.getFileSystem() == FileSystems.getDefault() && Files.isDirectory(from)) {
			return true;
		} else if (from instanceof QuiltJoinedPath) {
			QuiltJoinedPath path = (QuiltJoinedPath) from;
			QuiltJoinedFileSystem fs = path.getFileSystem();
			for (int i = 0; i < fs.getBackingPathCount(); i++) {
				Path backingPath = fs.getBackingPath(i, path);
				if (backingPath.getFileSystem() == FileSystems.getDefault()) {
					return true;
				}
			}
			return true;
		} else {
			return false;
		}
	}
}
