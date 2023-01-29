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

package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class FilePreloadHelper {

	private static final AtomicInteger COUNT = new AtomicInteger();
	private static final ArrayDeque<Path> PATHS = new ArrayDeque<>();
	private static volatile Thread preloader;

	public static void preLoad(Path path) {
		if (path.getFileSystem() != FileSystems.getDefault()) {
			return;
		}

		synchronized (PATHS) {
			PATHS.add(path);
			if (preloader == null) {
				String name = "QuiltLoader Cache Preloader #" + COUNT.incrementAndGet();
				preloader = new Thread(FilePreloadHelper::runPathLoaderThread, name);
				preloader.setDaemon(true);
				preloader.start();
			}
		}
	}

	private static void runPathLoaderThread() {

		final byte[] buffer = new byte[1 << 12];

		while (true) {
			Path next = null;
			synchronized (PATHS) {
				next = PATHS.poll();
				if (next == null) {
					preloader = null;
					return;
				}
			}

			try (InputStream stream = Files.newInputStream(next, StandardOpenOption.READ)) {

				while (stream.read(buffer) > 0) {
					// Read until there's nothing left
				}

			} catch (IOException e) {
				Log.warn(LogCategory.CACHE, "Unable to preload " + next, e);
			}
		}
	}
}
