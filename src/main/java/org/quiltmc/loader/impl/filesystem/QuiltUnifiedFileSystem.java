package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** General-purpose {@link FileSystem}, used when building the transform cache. Also intended to replace the various
 * zip/memory file systems currently in use. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltUnifiedFileSystem extends QuiltMapFileSystem<QuiltUnifiedFileSystem, QuiltUnifiedPath> {

	private boolean readOnly = false;

	// FIXME TODO: Make a cache that doesn't need to copy every file into memory if the source is a zip path!
	// also change the transform cache to *not* copy files into it when generating - only converting the links to normal
	// paths

	QuiltUnifiedFileSystem(String name, boolean uniqueify) {
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
	public FileSystemProvider provider() {
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
	public Iterable<FileStore> getFileStores() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}
}
