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
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.plugin.solver.QuiltFileHasher;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class FileHasherImpl implements QuiltFileHasher {

	public static final int HASH_LENGTH = HashUtil.SHA1_HASH_LENGTH;

	public final Map<Path, byte[]> pathHashCache = new ConcurrentHashMap<>();
	private final Function<Path, Path> getParentPath;

	public FileHasherImpl(Function<Path, Path> getParentPath) {
		this.getParentPath = getParentPath;
	}

	@Override
	public int getHashLength() {
		return HASH_LENGTH;
	}

	@Override
	public byte[] computeNormalHash(Path path) throws IOException {
		return computeHash(path, false);
	}

	@Override
	public byte[] computeRecursiveHash(Path folder) throws IOException {
		return computeHash(folder, true);
	}

	private byte[] computeHash(Path path, boolean recurseFolders) throws IOException {

		byte[] hash = pathHashCache.get(path);
		if (hash != null) {
			return Arrays.copyOf(hash, HASH_LENGTH);
		}

		Path originalPath = path;

		if (getParentPath != null) {
			while (path.getFileSystem() != FileSystems.getDefault()) {
				Path parent;
				while ((parent = path.getParent()) != null) {
					path = parent;
				}
				path = getParentPath.apply(path);
				if (path == null) {
					path = originalPath;
					break;
				}
			}
		}

		if (recurseFolders && FasterFiles.isDirectory(path)) {
			return computeRecursiveHash0(path);
		}

		try {
			hash = pathHashCache.computeIfAbsent(path, p2 -> {
				try {
					return HashUtil.computeHash(p2);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}

		return Arrays.copyOf(hash, HASH_LENGTH);
	}

	private static byte[] computeRecursiveHash0(Path path) throws IOException {
		final byte[] hash = new byte[HASH_LENGTH];

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

			void xorNameHash(Path inner) {
				Path name = path.relativize(inner);
				HashUtil.xorHash(hash, HashUtil.computeHash(name.toString()));
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				// To ensure we record empty directories
				xorNameHash(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				xorNameHash(file);
				HashUtil.xorHash(hash, HashUtil.computeHash(file));
				return FileVisitResult.CONTINUE;
			}
		});

		return hash;
	}
}
