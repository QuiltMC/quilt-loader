/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl.plugin.fabric;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModLocation;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.fabric.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.fabric.metadata.ParseMetadataException;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;
import org.quiltmc.loader.impl.plugin.BuiltinQuiltPlugin;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class StandardFabricPlugin extends BuiltinQuiltPlugin {

	@Override
	public ModLoadOption[] scanZip(Path root, ModLocation location, PluginGuiTreeNode guiNode) throws IOException {

		Path parent = context().manager().getParent(root);

		if (!parent.getFileName().toString().endsWith(".jar")) {
			return null;
		}

		return scan0(root, QuiltLoaderGui.iconJarFile(), location, true, guiNode);
	}

	@Override
	public ModLoadOption[] scanFolder(Path folder, ModLocation location, PluginGuiTreeNode guiNode) throws IOException {
		return scan0(folder, QuiltLoaderGui.iconFolder(), location, false, guiNode);
	}

	private ModLoadOption[] scan0(Path root, QuiltLoaderIcon fileIcon, ModLocation location, boolean isZip, PluginGuiTreeNode guiNode) throws IOException {
		Path fmj = root.resolve("fabric.mod.json");
		if (!FasterFiles.isRegularFile(fmj)) {
			return null;
		}

		try {
			FabricLoaderModMetadata meta = FabricModMetadataReader.parseMetadata(fmj);

			Path from = root;
			if (isZip) {
				from = context().manager().getParent(root);
			}

			jars: for (NestedJarEntry jarEntry : meta.getJars()) {
				String jar = jarEntry.getFile();
				Path inner = root;
				for (String part : jar.split("/")) {
					if ("..".equals(part)) {
						continue jars;
					}
					inner = inner.resolve(part);
				}

				if (inner == from) {
					continue;
				}

				if (!FasterFiles.exists(inner)) {
					Log.warn(LogCategory.DISCOVERY, "Didn't find nested jar " + inner + " in " + context().manager().describePath(from));
					PluginGuiTreeNode missingJij = guiNode.addChild(QuiltLoaderText.of(inner.toString()), SortOrder.ALPHABETICAL_ORDER);
					missingJij.mainIcon(QuiltLoaderGui.iconJarFile());
					missingJij.addChild(QuiltLoaderText.translate("fabric.jar_in_jar.missing"))//
						.setDirectLevel(WarningLevel.CONCERN);
					continue;
				}

				PluginGuiTreeNode jarNode = guiNode.addChild(QuiltLoaderText.of(jar), SortOrder.ALPHABETICAL_ORDER);
				context().addFileToScan(inner, jarNode, false);
			}

			boolean mandatory = location.isDirect();
			// a mod needs to be remapped if we are in a development environment, and the mod
			// did not come from the classpath
			boolean requiresRemap = !location.onClasspath() && QuiltLoader.isDevelopmentEnvironment();
			return new ModLoadOption[] { new FabricModOption(context(), meta, from, fileIcon, root, mandatory, requiresRemap) };
		} catch (ParseMetadataException parse) {
			QuiltLoaderText title = QuiltLoaderText.translate("gui.text.invalid_metadata.title", "fabric.mod.json", parse.getMessage());
			QuiltDisplayedError error = context().reportError(title);
			String describedPath = context().manager().describePath(fmj);
			error.appendReportText("Invalid 'fabric.mod.json' metadata file:" + describedPath);
			error.appendDescription(QuiltLoaderText.translate("gui.text.invalid_metadata.desc.0", describedPath));
			error.appendThrowable(parse);
			context().manager().getRealContainingFile(root).ifPresent(real ->
					error.addFileViewButton(QuiltLoaderText.translate("button.view_file"), real)
					.icon(QuiltLoaderGui.iconJarFile().withDecoration(QuiltLoaderGui.iconFabric()))
			);

			guiNode.addChild(QuiltLoaderText.translate("gui.text.invalid_metadata", parse.getMessage()))//TODO: translate
				.setError(parse, error);
			return null;
		}
	}
}
