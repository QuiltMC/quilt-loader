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

package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class LoaderUtil {
	public static String getClassFileName(String className) {
		return className.replace('.', '/').concat(".class");
	}

	public static String getClassNameFromTransformCache(String name) {
		name = name.substring(name.indexOf('/', 1) + 1); // remove /mod_id/
		name = name.replace('/', '.');
		return name.substring(0, name.length() - 6); // remove .class
	}

	public static Path normalizePath(Path path) {
		if (path == null) {
			return null;
		} else if (Files.exists(path)) {
			return normalizeExistingPath(path);
		} else {
			return path.toAbsolutePath().normalize();
		}
	}
	public static Path normalizeExistingPath(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	public static boolean hasMacOs() {
		return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac");
	}

	public static boolean hasAwtSupport() {
		if (hasMacOs()) {
			// check for JAVA_STARTED_ON_FIRST_THREAD_<pid> which is set if -XstartOnFirstThread is used
			// -XstartOnFirstThread is incompatible with AWT (force enables embedded mode)
			for (String key : System.getenv().keySet()) {
				if (key.startsWith("JAVA_STARTED_ON_FIRST_THREAD_")) return false;
			}
		}

		return true;
	}
}
