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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.QuiltFileSystems.ExtendedFileSystemRef;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltFileSystemsImpl {

	public static ExtendedFileSystemRef createExtendedFileSystem(String name) {
		return new FsRef(new QuiltUnifiedFileSystem(name, true));
	}

	public static ExtendedFileSystemRef loadZipFile(String name, Path zipFile) throws IOException {
		return new FsRef(new QuiltZipFileSystem(name, zipFile, "", ZipHandling.PLAIN));
	}

	public static ExtendedFileSystemRef loadJarFile(String name, Path jarFile) throws IOException {
		return new FsRef(new QuiltZipFileSystem(name, jarFile, "", ZipHandling.JAR));
	}

	public static ExtendedFileSystemRef loadZipFileCopyOnWrite(String name, Path zipFile) throws IOException {
		QuiltUnifiedFileSystem fs = new QuiltUnifiedFileSystem(name, true);
		ZipMounter.mountZipAt(zipFile, fs.root, "", ZipHandling.PLAIN);
		return new FsRef(fs);
	}

	public static ExtendedFileSystemRef loadJarFileCopyOnWrite(String name, Path jarFile) throws IOException {
		QuiltUnifiedFileSystem fs = new QuiltUnifiedFileSystem(name, true);
		ZipMounter.mountZipAt(jarFile, fs.root, "", ZipHandling.JAR);
		return new FsRef(fs);
	}

	private static final class FsRef extends ExtendedFileSystemRef {

		FsRef(QuiltUnifiedFileSystem fs) {
			super(fs, fs.root);
		}

		FsRef(QuiltZipFileSystem fs) {
			super(fs, fs.root);
		}

		@Override
		public void markAsReadOnly() {
			((QuiltMapFileSystem<?, ?>) quiltFileSystem).switchToReadOnly();
		}
	}
}
