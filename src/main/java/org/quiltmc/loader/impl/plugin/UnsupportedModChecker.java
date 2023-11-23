/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class UnsupportedModChecker {

	static UnsupportedModType checkFolder(Path folder) throws IOException {
		return null;
	}

	static UnsupportedModType checkUnknownFile(Path file) throws IOException {
		return null;
	}

	static UnsupportedModType checkZip(Path zipFile, Path zipRoot) throws IOException {
		// (Neo)Forge check
		UnsupportedModType type = checkForForgeMod(zipRoot);
		if (type != null) {
			return type;
		}
		// ModLoader check
		type = checkForModloaderMod(zipRoot);
		if (type != null) {
			return type;
		}
		// TODO: Other checks!
		return null;
	}

	private static UnsupportedModType checkForModloaderMod(Path zipRoot) throws IOException {
		for (Path child : FasterFiles.getChildren(zipRoot)) {
			if (!FasterFiles.isRegularFile(child)) {
				continue;
			}
			String fileName = child.getFileName().toString();
			if (fileName.startsWith("mod_") && fileName.endsWith(".class") && !fileName.contains("$")) {
				try {
					ClassReader cr = new ClassReader(Files.readAllBytes(child));
					if ("BaseMod".equals(cr.getSuperName())) {
						return new RisugamisModLoaderMod();
					}
				} catch (IOException ignored) {
					// It's a bit odd, but if we can't read 
					continue;
				}
			}
		}
		return null;
	}

	private static UnsupportedModType checkForForgeMod(Path zipRoot) {
		// Older forge
		Path mcmodInfo = zipRoot.resolve("mcmod.info");
		if (FasterFiles.exists(mcmodInfo)) {
			return new UnsupportedForgeMod(false);
		}
		// Modern (neo)forge
		Path modsToml = zipRoot.resolve("META-INF/mods.toml");
		if (FasterFiles.exists(modsToml)) {
			boolean isNeoforge = false;
			try (BufferedReader br = Files.newBufferedReader(modsToml)) {
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					int idxModId = line.indexOf("modId");
					int idxNeoforge = line.indexOf("\"neoforge\"");
					if (idxModId == 0 && idxNeoforge > 0) {
						// Just assume it's a dependency
						isNeoforge = true;
						break;
					}
				}
			} catch (IOException ignored) {
				// It's okay if we can't read it
			}
			return new UnsupportedForgeMod(isNeoforge);
		}
		return null;
	}

	static abstract class UnsupportedModType {
		final String type;

		UnsupportedModType(String type) {
			this.type = type;
		}

		abstract void addToGui(PluginGuiTreeNode guiNode);
	}

	static final class RisugamisModLoaderMod extends UnsupportedModType {

		RisugamisModLoaderMod() {
			super("risugamis_modloader");
		}

		@Override
		void addToGui(PluginGuiTreeNode guiNode) {
			guiNode.addChild(QuiltLoaderText.translate("unsupported_mod.risugamis_modloader.guiNode")).setDirectLevel(WarningLevel.WARN);
		}
	}

	static final class UnsupportedForgeMod extends UnsupportedModType {

		UnsupportedForgeMod(boolean neoforge) {
			super(neoforge ? "neoforge" : "forge");
		}

		@Override
		void addToGui(PluginGuiTreeNode guiNode) {
			guiNode.addChild(QuiltLoaderText.translate("unsupported_mod." + type + ".guiNode")).setDirectLevel(WarningLevel.WARN);
		}
	}
}
