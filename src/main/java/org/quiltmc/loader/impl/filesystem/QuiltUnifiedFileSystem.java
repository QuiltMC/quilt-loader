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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.api.ExtendedFileSystem;
import org.quiltmc.loader.api.MountOption;
import org.quiltmc.loader.api.NotDynamicFileException;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedCopyOnWriteFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedDynamicFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedMountedFile;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** General-purpose {@link FileSystem}, used when building the transform cache. Also intended to replace the various
 * zip/memory file systems currently in use. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltUnifiedFileSystem extends QuiltMapFileSystem<@NotNull QuiltUnifiedFileSystem, @NotNull QuiltUnifiedPath> implements ExtendedFileSystem {

	private boolean readOnly = false;

	public QuiltUnifiedFileSystem(String name, boolean uniqueify) {
		super(QuiltUnifiedFileSystem.class, QuiltUnifiedPath.class, name, uniqueify);
		addEntryAndParentsUnsafe(new QuiltUnifiedFolderWriteable(root));
	}

	@Override
	protected boolean startWithConcurrentMap() {
		return true;
	}

	@Override
	QuiltUnifiedPath createPath(@Nullable QuiltUnifiedPath parent, String name) {
		return new QuiltUnifiedPath(this, parent, name);
	}

	@Override
	public QuiltUnifiedFileSystemProvider provider() {
		return QuiltUnifiedFileSystemProvider.instance();
	}

	/** Disallows all modification. */
	@Override
	public void switchToReadOnly() {
		super.switchToReadOnly();
		readOnly = true;
	}

	@Override
	public boolean isPermanentlyReadOnly() {
		return readOnly;
	}

	@Override
	public boolean isReadOnly() {
		return isPermanentlyReadOnly();
	}

	@Override
	public Path copyExt(Path source, Path target, CopyOption... options) throws IOException {
		FileSystem srcFS = source.getFileSystem();
		if (srcFS instanceof ExtendedFileSystem) {
			ExtendedFileSystem srcExtFS = (ExtendedFileSystem) srcFS;

			// Check the boolean first since it saves us from constructing an exception

			final Supplier<byte[]> dynamicSource;

			if (srcFS instanceof QuiltUnifiedFileSystem) {
				synchronized (srcFS) {
					QuiltUnifiedEntry entry = ((QuiltUnifiedFileSystem) srcFS).getEntry(source);
					if (entry instanceof QuiltUnifiedDynamicFile) {
						dynamicSource = ((QuiltUnifiedDynamicFile) entry).supplier;
					} else {
						dynamicSource = null;
					}
				}
			} else {
				if (srcExtFS.isDynamicFile(source)) {
					Supplier<byte[]> supplier = null;
					try {
						supplier = srcExtFS.readDynamicFileSource(source);
					} catch (NotDynamicFileException ignored) {
						// Can happen due to threading or other issues
					}
					dynamicSource = supplier;
				} else {
					dynamicSource = null;
				}
			}

			if (dynamicSource != null) {
				boolean canExist = false;

				for (CopyOption option : options) {
					if (option instanceof StandardCopyOption) {
						switch ((StandardCopyOption) option) {
							case REPLACE_EXISTING: {
								canExist = true;
								break;
							}
							case COPY_ATTRIBUTES: {
								// Ignored
								break;
							}
							case ATOMIC_MOVE:
							default: {
								throw new UnsupportedOperationException(option.toString());
							}
						}
					} else if (option == LinkOption.NOFOLLOW_LINKS) {
						// Ignored
					} else {
						throw new UnsupportedOperationException(option.toString());
					}
				}


				synchronized (this) {
					QuiltUnifiedEntry dstEntry = getEntry(target);

					if (canExist) {
						provider().deleteIfExists(target);
					} else if (dstEntry != null) {
						throw new FileAlreadyExistsException(target.toString());
					}
					return createDynamicFile(target, dynamicSource);
				}
			}

			return copy(source, target, options);

		} else {
			return copy(source, target, options);
		}
	}

	@Override
	public Path copyOnWrite(Path source, Path target, CopyOption... options) throws IOException {
		FileSystem srcFS = source.getFileSystem();
		if (srcFS instanceof CachedFileSystem) {
			CachedFileSystem cached = (CachedFileSystem) srcFS;
			if (!cached.isPermanentlyReadOnly()) {
				return copy(source, target, options);
			}
		} else {
			return copy(source, target, options);
		}
		QuiltUnifiedPath dst = provider().toAbsolutePath(target);
		boolean canExist = false;

		for (CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				canExist = true;
			}
		}

		synchronized (this) {
			if (canExist) {
				provider().deleteIfExists(dst);
			} else if (getEntry(dst) != null) {
				throw new FileAlreadyExistsException(dst.toString());
			}
			addEntryRequiringParent(new QuiltUnifiedCopyOnWriteFile(dst, source));
		}
		return dst;
	}

	@Override
	public Path mount(Path source, Path target, MountOption... options) throws IOException {
		QuiltUnifiedPath dst = provider().toAbsolutePath(target);

		boolean canExist = false;
		boolean readOnly = false;
		boolean copyOnWrite = false;

		for (MountOption option : options) {
			switch (option) {
				case REPLACE_EXISTING: {
					canExist = true;
					break;
				}
				case COPY_ON_WRITE: {
					copyOnWrite = true;
					break;
				}
				case READ_ONLY: {
					readOnly = true;
					break;
				}
				default: {
					throw new IllegalStateException("Unknown MountOption " + option);
				}
			}
		}

		if (copyOnWrite && readOnly) {
			throw new IllegalArgumentException("Can't specify both READ_ONLY and COPY_ON_WRITE : " + Arrays.toString(options));
		}

		synchronized (this) {
			QuiltUnifiedEntry dstEntry = getEntry(dst);

			if (canExist) {
				provider().deleteIfExists(dst);
			} else if (dstEntry != null) {
				throw new FileAlreadyExistsException(dst.toString());
			}

			if (copyOnWrite) {
				dstEntry = new QuiltUnifiedCopyOnWriteFile(dst, source);
			} else {
				dstEntry = new QuiltUnifiedMountedFile(dst, source, readOnly);
			}
			addEntryRequiringParent(dstEntry);
			return dst;
		}
	}

	@Override
	public boolean isMountedFile(Path file) {
		return getEntry(file) instanceof QuiltUnifiedMountedFile;
	}

	@Override
	public boolean isCopyOnWrite(Path file) {
		return getEntry(file) instanceof QuiltUnifiedCopyOnWriteFile;
	}

	@Override
	public Path readMountTarget(Path file) throws IOException {
		QuiltUnifiedEntry entry = getEntry(file);
		if (entry instanceof QuiltUnifiedMountedFile) {
			return ((QuiltUnifiedMountedFile) entry).to;
		} else {
			throw new NotLinkException(file.toString() + " is not a mounted file!");
		}
	}

	@Override
	public Path createDynamicFile(Path file, Supplier<byte[]> supplier) throws IOException {
		QuiltUnifiedPath dst = provider().toAbsolutePath(file);
		addEntryRequiringParent(new QuiltUnifiedDynamicFile(dst, supplier));
		return dst;
	}

	@Override
	public boolean isDynamicFile(Path file) {
		return getEntry(file) instanceof QuiltUnifiedDynamicFile;
	}

	@Override
	public Supplier<byte[]> readDynamicFileSource(Path file) throws NotDynamicFileException {
		QuiltUnifiedEntry entry = getEntry(file);
		if (entry instanceof QuiltUnifiedDynamicFile) {
			return ((QuiltUnifiedDynamicFile) entry).supplier;
		} else {
			throw new NotDynamicFileException(file.toString());
		}
	}
}
