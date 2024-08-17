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

package org.quiltmc.loader.impl.patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.patch.reflections.ReflectionsClassPatcher;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class PatchLoader {
	private static final Map<String, byte[]> patchedClasses = new HashMap<>();

	public static void load() {
		ReflectionsClassPatcher.load(patchedClasses);

		if (Boolean.getBoolean(SystemProperties.DEBUG_DUMP_PATCHED_CLASSES)) {
			Path root = QuiltLoader.getGameDir().resolve("quilt_loader_patched_classes");
			try {
				Files.createDirectories(root);
				Files.write(root.resolve("FILE_LIST.txt"), patchedClasses.keySet().stream().sorted().collect(Collectors.toList()));
				for (Map.Entry<String, byte[]> entry : patchedClasses.entrySet()) {
					Path file = root.resolve(entry.getKey().replace(".", root.getFileSystem().getSeparator()) + ".class");
					Files.createDirectories(file.getParent());
					Files.write(file, entry.getValue());
				}
			} catch (IOException e) {
				throw new Error(
					"Failed to save patched classes! (If you don't need them then remove '-D"
						+ SystemProperties.DEBUG_DUMP_PATCHED_CLASSES + "=true from your VM options)", e
				);
			}
		}
	}

	public static byte[] getNewPatchedClass(String name) {
		byte[] patched = patchedClasses.get(name);
		if (patched == null) {
			Log.warn(LogCategory.GENERAL, "Unknown patch class " + name + ", we only know of " + patchedClasses.keySet());
		}
		return patched;
	}
}
