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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ExtendedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderReadOnly;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedMountedFile;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A read-only file system that only caches the locations of zip entries rather than their zip contents. This is
 * slightly more flexible than java's zip file system since it can have a different "root" than the real root of a zip
 * (useful for the transform cache). This also exists because (in java 8) the ZipFileSystem has a lot of bugs.
 * <p>
 * WARNING: Every new {@link InputStream} and {@link SeekableByteChannel} returned by this file system relies on the
 * input path's {@link SeekableByteChannel#position(long)} method to skip to the correct location. As such you should
 * only use this if the backing path supports efficient random access (generally {@link QuiltMemoryFileSystem} supports
 * this if it's not read-only, or the "compress" constructor argument is false). */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltZipFileSystem extends QuiltMapFileSystem<@NotNull QuiltZipFileSystem, @NotNull QuiltZipPath>
	implements ReadOnlyFileSystem, ExtendedFileSystem {

	static final boolean DEBUG_TEST_READING = true;

	public QuiltZipFileSystem(String name, Path zipFrom, String zipPathPrefix) throws IOException {
		this(name, zipFrom, zipPathPrefix, ZipHandling.PLAIN);
	}

	public QuiltZipFileSystem(String name, Path zipFrom, String zipPathPrefix, ZipHandling zip) throws IOException {
		super(QuiltZipFileSystem.class, QuiltZipPath.class, name, true);

		if (DEBUG_TEST_READING) {
			System.out.println("new QuiltZipFileSystem ( "  + name + ", from " + zipFrom + " )");
		}

		// Ensure root exists - empty zips wouldn't create this otherwise
		addEntryAndParents(new QuiltUnifiedFolderWriteable(root));

		ZipMounter.mountZipAt(zipFrom, root, zipPathPrefix, zip);

		switchToReadOnly();

		QuiltZipFileSystemProvider.PROVIDER.register(this);
		validate();
		dumpEntries(name);
	}

	@Override
	protected boolean startWithConcurrentMap() {
		return false;
	}

	/** Constructs a new {@link QuiltZipFileSystem} that only exposes a single sub-folder of a larger
	 * {@link QuiltZipFileSystem}. */
	public QuiltZipFileSystem(String name, @NotNull QuiltZipPath newRoot) {
		super(QuiltZipFileSystem.class, QuiltZipPath.class, name, true);

		mountFolder(newRoot, getRoot(), ZipMountType.READ_ONLY);

		QuiltZipFileSystemProvider.PROVIDER.register(this);

		if (!isDirectory(root)) {
			throw new IllegalStateException("Missing root???");
		}
	}

	public static void mountFolder(QuiltZipPath src, QuiltMapPath<?, ?> dst, ZipMountType mountType) {
		QuiltUnifiedEntry entryFrom = src.fs.getEntry(src);
		if (entryFrom instanceof QuiltUnifiedFolderReadOnly) {
			// QuiltZipFolder does store subfolders that are part of the original FS, so we need to fully copy it
			QuiltMapPath<?, ?>[] srcChildren = ((QuiltUnifiedFolderReadOnly) entryFrom).children;
			if (mountType == ZipMountType.READ_ONLY) {
				QuiltMapPath<?, ?>[] dstChildren = new QuiltMapPath<?, ?>[srcChildren.length];
				for (int i = 0; i < srcChildren.length; i++) {
					QuiltMapPath<?, ?> srcChild = srcChildren[i];
					QuiltMapPath<?, ?> dstChild = dst.resolve(srcChild.name);
					mountFolder((QuiltZipPath) srcChild, dstChild, mountType);
					dstChildren[i] = dstChild;
				}
				dst.fs.addEntryWithoutParentsUnsafe(new QuiltUnifiedFolderReadOnly(dst, dstChildren));
			} else {
				QuiltUnifiedFolderWriteable dstFolder = new QuiltUnifiedFolderWriteable(dst);
				for (QuiltMapPath<?, ?> srcChild : srcChildren) {
					QuiltMapPath<?, ?> dstChild = dst.resolve(srcChild.name);
					mountFolder((QuiltZipPath) srcChild, dstChild, mountType);
					dstFolder.children.add(dstChild);
				}
				dst.fs.addEntryWithoutParentsUnsafe(dstFolder);
			}
		} else if (entryFrom instanceof QuiltZipFile) {
			QuiltZipFile from = (QuiltZipFile) entryFrom;
			dst.fs.addSource(from.source);
			dst.fs.addEntryWithoutParentsUnsafe(mountType.create(dst, from.source, from.offset, from.compressedSize, from.uncompressedSize, from.isCompressed));
		} else if (entryFrom instanceof QuiltUnifiedMountedFile) {
			// Used for Multi-Release jars
			// This isn't ideal, as it will continue to point to the original file system
			QuiltUnifiedMountedFile from = (QuiltUnifiedMountedFile) entryFrom;
			dst.fs.addEntryWithoutParentsUnsafe(new QuiltUnifiedMountedFile(dst, from.to, true));
		} else {
			// This isn't meant to happen, it means something got constructed badly
			throw new IllegalArgumentException("Unknown source entry " + entryFrom);
		}
	}

	/** Writes a "Quilt compressed file system" to the given destination, which can be read by
	 * {@link #QuiltZipFileSystem(String, Path, String)} - likely more quickly than a regular zip file. The source must
	 * be a folder. The output file will be of similar size to a regular zip of the same contents.
	 * 
	 * @param src The source folder to copy from.
	 * @param dst The destination file to copy to. This must not already exist.
	 * @throws IOException if anything goes wrong while writing the file or reading the source files. */
	public static void writeQuiltCompressedFileSystem(Path src, Path dst) throws IOException {
		new QuiltZipCustomCompressedWriter(src, dst).write();
	}

	@Override
	QuiltZipPath createPath(@Nullable QuiltZipPath parent, String name) {
		return new QuiltZipPath(this, parent, name);
	}

	@Override
	public FileSystemProvider provider() {
		return QuiltZipFileSystemProvider.instance();
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	// FasterFileSystem

	@Override
	public boolean isExecutable(Path path) {
		return exists(path);
	}

	// ExtendedFileSystem

	// These are supported due to multi-release jars

	@Override
	public boolean isMountedFile(Path file) {
		return getEntry(file) instanceof QuiltUnifiedMountedFile;
	}

	// Copy-on-write is unsupported

	@Override
	public Path readMountTarget(Path file) throws IOException {
		QuiltUnifiedEntry entry = getEntry(file);
		if (entry instanceof QuiltUnifiedMountedFile) {
			return ((QuiltUnifiedMountedFile) entry).to;
		} else {
			throw new NotLinkException(file.toString() + " is not a mounted file!");
		}
	}

	// Custom classes to grab the real offset while reading the zip

	static final class CountingInputStream extends InputStream {

		final InputStream stream;
		long offset;

		protected CountingInputStream(InputStream in) {
			this.stream = in;
		}

		@Override
		public int read() throws IOException {
			offset++;
			return stream.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = stream.read(b, off, len);
			if (read > 0) {
				offset += read;
			}
			return read;
		}

		@Override
		public long skip(long n) throws IOException {
			long skipped = stream.skip(n);
			if (skipped > 0) {
				offset += skipped;
			}
			return skipped;
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}
	}

	static final class CustomPushbackInputStream extends PushbackInputStream {
		public CustomPushbackInputStream(CountingInputStream in, int size) {
			super(in, size);
		}

		public long getOffset() {
			return ((CountingInputStream) in).offset - buf.length + pos;
		}
	}

	static final class CustomZipInputStream extends ZipInputStream {
		public CustomZipInputStream(CountingInputStream in) {
			super(in);
			this.in = new CustomPushbackInputStream(in, buf.length);
		}

		public long getOffset() {
			return ((CustomPushbackInputStream) in).getOffset();
		}
	}
}
