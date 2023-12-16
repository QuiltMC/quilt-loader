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
import java.util.SortedMap;
import java.util.SortedSet;

import org.objectweb.asm.ClassReader;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.impl.gui.QuiltStatusNode;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class UnsupportedModChecker {

	static UnsupportedModDetails checkFolder(Path folder) throws IOException {
		return new UnknownMod();
	}

	static UnsupportedModDetails checkUnknownFile(Path file) throws IOException {
		return new UnknownMod();
	}

	static UnsupportedModDetails checkZip(Path zipFile, Path zipRoot) throws IOException {
		// (Neo)Forge check
		UnsupportedModDetails type = checkForForgeMod(zipRoot);
		if (type != null) {
			return type;
		}
		// ModLoader check
		type = checkForModloaderMod(zipRoot);
		if (type != null) {
			return type;
		}
		// TODO: Other checks!
		return new UnknownMod();
	}

	private static UnsupportedModDetails checkForModloaderMod(Path zipRoot) throws IOException {
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

	private static UnsupportedModDetails checkForForgeMod(Path zipRoot) {
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

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public enum UnsupportedType {
		UNKNOWN("unknown"),
		RISUGAMIS_MODLOADER("risugamis_modloader") {
			@Override
			QuiltDisplayedError createMessage(SortedMap<String, UnsupportedModDetails> files) {
				QuiltDisplayedError message = super.createMessage(files);
				message.addOpenLinkButton(QuiltLoaderText.of("Check RGML Quilt"), "https://github.com/sschr15/rgml-quilt");
				return message;
			}
		},
		FORGE("forge"),
		NEOFORGE("neoforge");

		final String type;

		private UnsupportedType(String type) {
			this.type = type;
		}

		QuiltDisplayedError createMessage(SortedMap<String, UnsupportedModDetails> files) {
			String key = "unsupported_mod." + type;
			QuiltDisplayedError message = QuiltLoaderGui.createError();
			message.title(QuiltLoaderText.translate(key + ".title", files.size()));
			message.setIcon(QuiltLoaderGui.iconLevelWarn());
			message.appendDescription(QuiltLoaderText.translate(key + ".desc"), QuiltLoaderText.of(" "));
			for (String file : files.keySet()) {
				message.appendDescription(QuiltLoaderText.of(file));
			}
			return message;
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static abstract class UnsupportedModDetails {
		// This is a class rather than just folded into the enum above
		// to allow individual files to have more specific description.
		final UnsupportedType type;

		UnsupportedModDetails(UnsupportedType type) {
			this.type = type;
		}

		void addToFilesNode(QuiltTreeNode guiNode) {
			String key = "unsupported_mod." + type.type + ".guiNode";
			guiNode.addChild(QuiltLoaderText.translate(key)).level(QuiltWarningLevel.WARN);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class UnknownMod extends UnsupportedModDetails {
		UnknownMod() {
			super(UnsupportedType.UNKNOWN);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class RisugamisModLoaderMod extends UnsupportedModDetails {
		RisugamisModLoaderMod() {
			super(UnsupportedType.RISUGAMIS_MODLOADER);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class UnsupportedForgeMod extends UnsupportedModDetails {
		UnsupportedForgeMod(boolean neoforge) {
			super(neoforge ? UnsupportedType.NEOFORGE : UnsupportedType.FORGE);
		}
	}
}
