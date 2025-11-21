/*
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

package org.quiltmc.loader.api.plugin.transformer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface TransformCache {
	List<ModLoadOption> getModsInCache();
	List<ModLoadOption> getAllMods();
	Map<String, String> getHiddenClasses();
	void hideClass(String className, String denyReason);
	Path getRoot(ModLoadOption mod);
	void forEachClassFile(ClassConsumer action);
	void forEachClassFile(ModLoadOption mod, ModClassConsumer action);

	@FunctionalInterface
	interface ClassConsumer {
		/**
		 * Consume a class and potentially transform it.
		 *
		 * @param mod       the mod which "owns" this class file
		 * @param className the name of the class in dot form (e.g. {@code net.minecraft.client.MinecraftClient$1}
		 * @return the transformed bytes, or null if nothing was changed. Use {@link TransformCache#hideClass(String, String)} to delete a class.
		 */
		byte @Nullable [] run(ModLoadOption mod, String className, Path file) throws IOException;
	}

	@FunctionalInterface
	interface ModClassConsumer {
		/**
		 * Consume a class and potentially transform it.
		 *
		 * @param className the name of the class in dot form (e.g. {@code net.minecraft.client.MinecraftClient$1}
		 * @return the transformed bytes, or null if nothing was changed. Use {@link TransformCache#hideClass(String, String)} to delete a class.
		 */
		byte @Nullable [] run(String className, Path file) throws IOException;
	}
}
