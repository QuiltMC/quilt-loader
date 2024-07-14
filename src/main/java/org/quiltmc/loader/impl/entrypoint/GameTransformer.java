/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

import org.quiltmc.loader.impl.util.ExceptionUtil;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.util.SimpleClassPath;
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
import java.util.zip.ZipError;
import java.util.zip.ZipFile;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)

public class GameTransformer {
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

	public void locateEntrypoints(QuiltLauncher launcher, List<Path> gameJars) {
		this.locateEntrypoints(launcher, null, gameJars);
	}
	public void locateEntrypoints(QuiltLauncher launcher, String namespace, List<Path> gameJars) {
		if (entrypointsLocated) {
			return;
		}

		patchedClasses = new HashMap<>();

		try (SimpleClassPath cp = new SimpleClassPath(gameJars)) {
			Function<String, ClassReader> classSource = name -> {
				byte[] data = patchedClasses.get(name);

				if (data != null) {
					return new ClassReader(data);
				}

				try {
					SimpleClassPath.CpEntry entry = cp.getEntry(LoaderUtil.getClassFileName(name));
					if (entry == null) return null;

					try (InputStream is = entry.getInputStream()) {
						return new ClassReader(is);
					} catch (IOException | ZipError e) {
						throw new RuntimeException(String.format("error reading %s in %s: %s", name, LoaderUtil.normalizePath(entry.getOrigin()), e), e);
					}
				} catch (IOException e) {
					throw ExceptionUtil.wrap(e);
				}
			};

			Map<String, ClassNode> tempClassNodes = new HashMap<>();
			Map<String, ClassNode> addedClassNodes = new HashMap<>();

			GamePatchContext context = new GamePatchContext() {

				@Override
				public ClassReader getClassSourceReader(String className) {
					return classSource.apply(className);
				}

				@Override
				public ClassNode getClassNode(String className) {
					ClassNode node = tempClassNodes.get(className);
					if (node == null) {
						node = GamePatch.readClass(getClassSourceReader(className));
						tempClassNodes.put(className, node);
					}
					return node;
				}

				@Override
				public void addPatchedClass(ClassNode node) {
					String key = node.name.replace('/', '.');
					if (tempClassNodes.get(key) == node) {
						addedClassNodes.put(key, node);
					} else if (addedClassNodes.containsKey(key)) {
						throw new RuntimeException("Duplicate addPatchedClasses call: " + key);
					} else {
						GameTransformer.this.addPatchedClass(node);
					}
				}
			};

			for (GamePatch patch : patches) {
				if (namespace == null)
					patch.process(launcher, context);
				else
					patch.process(launcher, namespace, context);
			}

			for (ClassNode node : addedClassNodes.values()) {
				addPatchedClass(node);
			}
		} catch (IOException e) {
			throw ExceptionUtil.wrap(e);
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
