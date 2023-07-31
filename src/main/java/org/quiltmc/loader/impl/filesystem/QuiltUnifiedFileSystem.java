package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.api.ExtendedFileSystem;
import org.quiltmc.loader.api.MountOption;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedCopyOnWriteFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedMountedFile;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** General-purpose {@link FileSystem}, used when building the transform cache. Also intended to replace the various
 * zip/memory file systems currently in use. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltUnifiedFileSystem extends QuiltMapFileSystem<QuiltUnifiedFileSystem, QuiltUnifiedPath> implements ExtendedFileSystem {

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
	public void close() throws IOException {

	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return isPermanentlyReadOnly();
	}

	@Override
	public Path copyOnWrite(Path source, Path target, CopyOption... options) throws IOException {
		FileSystem srcFS = source.getFileSystem();
		if (srcFS instanceof CachedFileSystem) {
			CachedFileSystem cached = (CachedFileSystem) srcFS;
			if (!cached.isPermanentlyReadOnly()) {
				System.out.println("Cannot copy-on-write " + cached.getClass() + " " + source + " " + source.getClass());
				return copy(source, target, options);
			}
		} else {
			System.out.println("Cannot copy-on-write other " + source + " " + source.getClass());
			return copy(source, target, options);
		}
		QuiltUnifiedPath dst = provider().toAbsolutePath(target);
		QuiltUnifiedEntry dstEntry = getEntry(dst);

		boolean canExist = false;

		for (CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				canExist = true;
			}
		}

		if (canExist) {
			provider().delete(dst);
		} else if (dstEntry != null) {
			throw new FileAlreadyExistsException(dst.toString());
		}

		addEntryRequiringParent(new QuiltUnifiedCopyOnWriteFile(dst, source));
		return dst;
	}

	@Override
	public Path mount(Path source, Path target, MountOption... options) throws IOException {
		QuiltUnifiedPath dst = provider().toAbsolutePath(target);
		QuiltUnifiedEntry dstEntry = getEntry(dst);

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


		if (canExist) {
			provider().delete(dst);
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
}
