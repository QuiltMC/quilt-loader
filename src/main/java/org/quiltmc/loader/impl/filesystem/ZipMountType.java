package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.util.zip.ZipEntry;

import org.quiltmc.loader.impl.filesystem.QuiltZipFile.CopyOnWriteZipFile;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.CustomZipInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public enum ZipMountType {
	READ_ONLY {
		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
			throws IOException {
			return new QuiltZipFile(path, source, entry, zip);
		}

		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
			int uncompressedSize, boolean isCompressed) {
			return new QuiltZipFile(path, source, offset, compressedSize, uncompressedSize, isCompressed);
		}
	},
	COPY_ON_WRITE {
		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
			throws IOException {
			return new CopyOnWriteZipFile(path, source, entry, zip);
		}

		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
			int uncompressedSize, boolean isCompressed) {

			return new CopyOnWriteZipFile(path, source, offset, compressedSize, uncompressedSize, isCompressed);
		}
	};

	abstract QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry,
		CustomZipInputStream zip) throws IOException;

	abstract QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
		int uncompressedSize, boolean isCompressed);
}
