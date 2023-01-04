/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.entrypoint;

import org.quiltmc.loader.impl.util.LoaderUtil;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GameTransformer {
	public static String appletMainClass;

	private final List<GamePatch> patches;
	private Map<String, byte[]> patchedClasses;
	private boolean entrypointsLocated = false;

	public GameTransformer(GamePatch... patches) {
		this.patches = Arrays.asList(patches);
	}

	private void addPatchedClass(ClassNode node) {
		String key = node.name.replace('/', '.');

		if (patchedClasses.containsKey(key)) {
			throw new RuntimeException("Duplicate addPatchedClasses call: " + key);
		}

		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		patchedClasses.put(key, writer.toByteArray());
	}

	public void locateEntrypoints(QuiltLauncher launcher, Path gameJar) {
		if (entrypointsLocated) {
			return;
		}

		patchedClasses = new HashMap<>();

		if (Files.isDirectory(gameJar)) {
			Function<String, ClassReader> classSource = name -> {
				byte[] data = patchedClasses.get(name);

				if (data != null) {
					return new ClassReader(data);
				}

				Path path = gameJar.resolve(LoaderUtil.getClassFileName(name));
				if(Files.notExists(path)) return null;

				try (InputStream is = Files.newInputStream(path)) {
					return new ClassReader(is);
				} catch (IOException e) {
					throw new UncheckedIOException(String.format("error reading %s in %s: %s", path.toAbsolutePath(), gameJar.toAbsolutePath(), e), e);
				}
			};

			for (GamePatch patch : patches) {
				patch.process(launcher, classSource, this::addPatchedClass);
			}
		} else {

			try (ZipFile zf = new ZipFile(gameJar.toFile())) {
				Function<String, ClassReader> classSource = name -> {
					byte[] data = patchedClasses.get(name);

					if (data != null) {
						return new ClassReader(data);
					}

					ZipEntry entry = zf.getEntry(LoaderUtil.getClassFileName(name));
					if (entry == null) return null;

					try (InputStream is = zf.getInputStream(entry)) {
						return new ClassReader(is);
					} catch (IOException e) {
						throw new UncheckedIOException(String.format("error reading %s in %s: %s", name, gameJar.toAbsolutePath(), e), e);
					}
				};

				for (GamePatch patch : patches) {
					patch.process(launcher, classSource, this::addPatchedClass);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(String.format("error reading %s: %s", gameJar.toAbsolutePath(), e), e);
			}
		}

		Log.debug(LogCategory.GAME_PATCH, "Patched %d class%s", patchedClasses.size(), patchedClasses.size() != 1 ? "s" : "");
		entrypointsLocated = true;
	}

	/**
	 * This must run first, contractually!
	 * @param className The class name,
	 * @return The transformed class data.
	 */
	public byte[] transform(String className) {
		return patchedClasses.get(className);
	}
}
