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

package org.quiltmc.loader.impl.patch.reflections;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.quiltmc.loader.api.FasterFileSystem;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Patch class that is transformed by {@link ReflectionsClassPatcher} to implement "org.reflections.vfs.Vfs.UrlType" */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ReflectionsPathUrlType {

	static {
		try {
			Class<?> cls = Class.forName("org.reflections.vfs.Vfs");
			Field field = cls.getDeclaredField("defaultUrlTypes");
			field.setAccessible(true);
			List<Object> list = (List<Object>) field.get(null);
			list.add(new ReflectionsPathUrlType());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to inject into reflections!", e);
		}
	}

	public boolean matches(URL url) throws Exception {
		// Basically only handle quilt file systems
		Path path = Paths.get(url.toURI());
		return path.getFileSystem() instanceof FasterFileSystem;
	}

	public ReflectionsPathDir createDir(URL url) throws Exception {
		return new ReflectionsPathDir(Paths.get(url.toURI()));
	}
}
