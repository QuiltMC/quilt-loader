/*
 * Copyright 2016 FabricMC
 * Copyright 2025 QuiltMC
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
package org.quiltmc.loader.impl.game.minecraft.patch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.transformer.QuiltTransformerPlugin;
import org.quiltmc.loader.api.plugin.transformer.QuiltTransformerPluginContext;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

// This is the transform cache equivalent of Fabric's GameTransformer
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class GameTransformerPlugin implements QuiltTransformerPlugin {
	private final List<GamePatch> patches;
	
	public GameTransformerPlugin(GamePatch... patches) {
		this.patches = Arrays.asList(patches);
	}

	@Override
	public void accept(QuiltTransformerPluginContext ctx) {
		ctx.createTransformation("quilt_loader_minecraft:entrypoint").before(QuiltTransformerPlugin.ROOT).register(cache -> {
			ModLoadOption minecraft = cache.getModsInCache().stream().filter(o -> o.id().equals("minecraft")).findFirst()
				.orElseThrow(() -> new IllegalStateException("Could not find Minecraft in transform cache!"));
			Path minecraftRoot = cache.getRoot(minecraft);
			Map<String, ClassNode> tempClassNodes = new HashMap<>();
			Map<String, ClassNode> addedClassNodes = new HashMap<>();
			GamePatchContext patchCtx = new GamePatchContext() {
					@Override
					public ClassReader getClassSourceReader(String className) {
						Path file = minecraftRoot.resolve(className.replace('.', '/') + ".class");
						
						if (!Files.exists(file)) {
							return null;
						}
						
						try {
							return new ClassReader(Files.readAllBytes(minecraftRoot.resolve(className.replace('.', '/') + ".class")));
						} catch (IOException ex) {
							throw new UncheckedIOException(ex);
						}
					}
					@Override
					public ClassNode getClassNode(String className) {
						return tempClassNodes.computeIfAbsent(className, name -> GamePatch.readClass(getClassSourceReader(name)));
					}
					
					@Override
					public void addPatchedClass(ClassNode node) {
						String key = node.name.replace('/', '.');
						if (tempClassNodes.get(key) == node) {
							addedClassNodes.put(key, node);
						} else if (addedClassNodes.containsKey(key)) {
							throw new RuntimeException("Duplicate addPatchedClasses call: " + key);
						} else {
							ClassWriter writer = new ClassWriter(0);
							node.accept(writer);
							try {
								Files.write(minecraftRoot.resolve(node.name + ".class"), writer.toByteArray());
							} catch(IOException ex) {
								throw new UncheckedIOException(ex);
							}
						}
          }
			};

			for (GamePatch patch : patches) {
				patch.process(QuiltLauncherBase.getLauncher(), QuiltLauncherBase.getLauncher().getTargetNamespace(), patchCtx);
			}
			for (ClassNode node : addedClassNodes.values()) {
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				try {
					Files.write(minecraftRoot.resolve(node.name + ".class"), writer.toByteArray());
				} catch(IOException ex) {
					throw new UncheckedIOException(ex);
				}
			}
		});
	}
}
