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
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@SuppressWarnings("unchecked") // TODO make more specific
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltMemoryFileSystemProvider extends FileSystemProvider {
	public QuiltMemoryFileSystemProvider() {}

	public static final String SCHEME = "quilt.mfs";

	static final String READ_ONLY_EXCEPTION = "This FileSystem is read-only";
	static final QuiltFSP<QuiltMemoryFileSystem> PROVIDER = new QuiltFSP<>(SCHEME);

	public static QuiltMemoryFileSystemProvider instance() {
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider instanceof QuiltMemoryFileSystemProvider) {
				return (QuiltMemoryFileSystemProvider) provider;
			}
		}
		throw new IllegalStateException("Unable to load QuiltMemoryFileSystemProvider via services!");
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		throw new IOException("Only direct creation is supported");
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		throw new FileSystemNotFoundException("Only direct creation is supported");
	}

	@Override
	public QuiltMemoryPath getPath(URI uri) {
		return PROVIDER.getFileSystem(uri).root.resolve(uri.getPath());
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		if (path instanceof QuiltMemoryPath) {
			QuiltMemoryPath p = (QuiltMemoryPath) path;
			QuiltMemoryEntry entry = p.fs.files.get(p.toAbsolutePath().normalize());
			if (entry instanceof QuiltMemoryFile) {
				return ((QuiltMemoryFile) entry).createInputStream();
			} else if (entry != null) {
				throw new FileSystemException("Cannot open an InputStream on a directory!");
			} else {
				throw new NoSuchFileException(path.toString());
			}
		} else {
			throw new IllegalArgumentException("The given path is not a QuiltMemoryPath!");
		}
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {

		if (!(path instanceof QuiltMemoryPath)) {
			throw new IllegalArgumentException("The given path is not a QuiltMemoryPath!");
		}

		QuiltMemoryPath p = (QuiltMemoryPath) path;
		QuiltMemoryPath normalizedPath = p.toAbsolutePath().normalize();
		QuiltMemoryEntry entry = p.fs.files.get(normalizedPath);

		if (!path.getFileSystem().isReadOnly()) {
			boolean create = false;
			for (OpenOption o : options) {
				if (o == StandardOpenOption.CREATE_NEW) {
					if (entry != null) {
						throw new IOException("File already exists: " + p);
					}
					create = true;
				} else if (o == StandardOpenOption.CREATE) {
					create = true;
				}
			}

			if (create && entry == null) {
				if (!normalizedPath.isAbsolute()) {
					throw new IOException("Cannot work above the root!");
				}

				QuiltMemoryEntry parent = p.fs.files.get(normalizedPath.getParent());
				if (parent == null) {
					throw new IOException("Cannot create a file if it's parent directory doesn't exist!");
				}
				if (!(parent instanceof QuiltMemoryFolder.ReadWrite)) {
					throw new IOException("Cannot create a file if it's parent is not a directory!");
				}
				p.fs.files.put(normalizedPath, entry = new QuiltMemoryFile.ReadWrite(normalizedPath));
				((QuiltMemoryFolder.ReadWrite) parent).children.add(normalizedPath);
			}
		}

		if (entry instanceof QuiltMemoryFile) {
			return ((QuiltMemoryFile) entry).createByteChannel(options);
		} else if (entry != null) {
			throw new FileSystemException("Cannot open an InputStream on a directory!");
		} else {
			throw new NoSuchFileException(path.toString());
		}
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		QuiltMemoryPath qmp = (QuiltMemoryPath) dir;

		final QuiltMemoryPath[] entries;
		QuiltMemoryEntry entry = qmp.fs.files.get(qmp);
		if (entry instanceof QuiltMemoryFolder.ReadOnly) {
			entries = ((QuiltMemoryFolder.ReadOnly) entry).children;
		} else if (entry instanceof QuiltMemoryFolder.ReadWrite) {
			entries = ((QuiltMemoryFolder.ReadWrite) entry).children
					.toArray(new QuiltMemoryPath[0]);
		} else {
			throw new NotDirectoryException("Not a directory: " + dir);
		}

		return new DirectoryStream<Path>() {

			boolean opened = false;
			boolean closed = false;

			@Override
			public void close() throws IOException {
				closed = true;
			}

			@Override
			public Iterator<Path> iterator() {
				if (opened) {
					throw new IllegalStateException("newDirectoryStream only supports a single iteration!");
				}
				opened = true;

				return new Iterator<Path>() {
					int index = 0;
					Path next;

					@Override
					public Path next() {
						if (next == null) {
							if (hasNext()) {
								return next;
							} else {
								throw new NoSuchElementException();
							}
						}
						Path path = next;
						next = null;
						return path;
					}

					@Override
					public boolean hasNext() {
						if (closed) {
							return false;
						}

						if (next != null) {
							return true;
						}

						for (; index < entries.length; index++) {
							Path at = entries[index];

							try {
								if (filter.accept(at)) {
									next = at;
									index++;
									return true;
								}
							} catch (IOException e) {
								throw new DirectoryIteratorException(e);
							}
						}

						return false;
					}
				};
			}
		};
	}

	private static QuiltMemoryPath toAbsQuiltPath(Path path) {
		Path p = path.toAbsolutePath().normalize();
		if (p instanceof QuiltMemoryPath) {
			return (QuiltMemoryPath) p;
		} else {
			throw new IllegalArgumentException("Only 'QuiltMemoryPath' is supported!");
		}
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		if (dir.getFileSystem().isReadOnly()) {
			throw new IOException(READ_ONLY_EXCEPTION);
		}
		QuiltMemoryPath d = toAbsQuiltPath(dir);
		QuiltMemoryPath parent = d.parent;

		QuiltMemoryEntry entry = d.fs.files.get(d);
		QuiltMemoryEntry parentEntry = d.fs.files.get(parent);

		if (entry != null) {
			throw new FileAlreadyExistsException(d.toString());
		}

		if (parentEntry instanceof QuiltMemoryFolder.ReadWrite) {

			QuiltMemoryFolder.ReadWrite parentFolder = (QuiltMemoryFolder.ReadWrite) parentEntry;
			d.fs.files.put(d, new QuiltMemoryFolder.ReadWrite(d));
			parentFolder.children.add(d);

		} else if (parentEntry != null) {
			throw new IOException("Parent file is not a directory " + parent);
		} else {
			throw new IOException("Parent is missing! " + parent);
		}
	}

	@Override
	public void delete(Path path) throws IOException {
		delete0(path, true);
	}

	@Override
	public boolean deleteIfExists(Path path) throws IOException {
		return delete0(path, false);
	}

	private static boolean delete0(Path path, boolean throwIfMissing) throws IOException {
		if (path.getFileSystem().isReadOnly()) {
			throw new IOException(READ_ONLY_EXCEPTION);
		}
		QuiltMemoryPath p = toAbsQuiltPath(path);
		QuiltMemoryEntry entry = p.fs.files.get(p);
		if (entry == null) {
			if (throwIfMissing) {
				throw new NoSuchFileException(p.toString());
			} else {
				return false;
			}
		}
		QuiltMemoryEntry pEntry = p.fs.files.get(p.parent);

		if (entry instanceof QuiltMemoryFolder.ReadWrite) {
			QuiltMemoryFolder.ReadWrite folder = (QuiltMemoryFolder.ReadWrite) entry;
			if (!folder.children.isEmpty()) {
				throw new DirectoryNotEmptyException(p.toString());
			}
		}

		if (pEntry == null) {
			throw new IOException("Missing parent folder to delete " + p + " from!");
		}

		((QuiltMemoryFolder.ReadWrite) pEntry).children.remove(p);
		p.fs.files.remove(p);
		return true;
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		if (target.getFileSystem().isReadOnly()) {
			throw new IOException(READ_ONLY_EXCEPTION);
		}
		QuiltMemoryPath src = toAbsQuiltPath(source);
		QuiltMemoryPath dst = toAbsQuiltPath(target);

		if (src.equals(dst)) {
			return;
		}

		QuiltMemoryEntry srcEntry = src.fs.files.get(src);
		QuiltMemoryEntry dstEntry = dst.fs.files.get(dst);

		if (srcEntry == null) {
			throw new NoSuchFileException(src.toString());
		}

		if (!(srcEntry instanceof QuiltMemoryFile)) {
			throw new IOException("Not a file: " + src);
		}

		QuiltMemoryFile srcFile = (QuiltMemoryFile) srcEntry;
		boolean canExist = false;

		for (CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				canExist = true;
			}
		}

		QuiltMemoryFile.ReadWrite dstFile;

		if (canExist) {
			if (dstEntry instanceof QuiltMemoryFolder.ReadWrite) {
				QuiltMemoryFolder.ReadWrite dstFolder = (QuiltMemoryFolder.ReadWrite) dstEntry;
				if (!dstFolder.children.isEmpty()) {
					throw new DirectoryNotEmptyException(dstEntry.path.toString());
				}
				dstEntry = dstFile = new QuiltMemoryFile.ReadWrite(dst);
				dst.fs.files.put(dst, dstEntry);
			} else if (dstEntry instanceof QuiltMemoryFile.ReadWrite) {
				dstFile = (QuiltMemoryFile.ReadWrite) dstEntry;
			} else if (dstEntry != null) {
				throw new IllegalStateException("Not a RW folder or file: " + dstEntry.getClass());
			} else {
				dstFile = null;
			}
		} else if (dstEntry != null) {
			throw new FileAlreadyExistsException(dst.toString());
		} else {
			dstFile = null;
		}

		if (dstFile == null) {
			QuiltMemoryEntry parent = dst.fs.files.get(dst.parent);
			if (parent == null) {
				throw new IOException("Missing parent folder! " + dst);
			} else if (parent instanceof QuiltMemoryFolder.ReadWrite) {
				QuiltMemoryFolder.ReadWrite parentFolder = (QuiltMemoryFolder.ReadWrite) parent;
				parentFolder.children.add(dst);
			} else {
				throw new IOException("Parent is not a folder! " + dst);
			}

			dstEntry = dstFile = new QuiltMemoryFile.ReadWrite(dst);
			dst.fs.files.put(dst, dstEntry);
		}

		dstFile.copyFrom(srcFile);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		if (target.getFileSystem().isReadOnly()) {
			throw new IOException(READ_ONLY_EXCEPTION);
		}

		if (isSameFile(source, target)) {
			return;
		}

		copy(source, target, options);
		delete(source);
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		path = path.toAbsolutePath().normalize();
		path2 = path2.toAbsolutePath().normalize();
		// We don't support links, so we can just check for equality
		return path.equals(path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return path.getFileName().toString().startsWith(".");
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return ((QuiltMemoryPath) path).fs.getFileStores().iterator().next();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		for (AccessMode mode : modes) {
			if (mode == AccessMode.WRITE && path.getFileSystem().isReadOnly()) {
				throw new IOException(READ_ONLY_EXCEPTION);
			}
		}
		QuiltMemoryPath quiltPath = toAbsQuiltPath(path);
		QuiltMemoryEntry entry = quiltPath.fs.files.get(quiltPath);
		if (entry == null) {
			throw new NoSuchFileException(quiltPath.toString());
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		QuiltMemoryPath qmp = toAbsQuiltPath(path);
		return qmp.fs.getFileAttributeView(qmp, type);
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {

		if (type == BasicFileAttributes.class) {
			QuiltMemoryPath qmp = (QuiltMemoryPath) path;
			return (A) qmp.fs.readAttributes(qmp);
		} else {
			throw new UnsupportedOperationException("Unsupported attributes " + type);
		}
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return PROVIDER.readAttributes(this, path, attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new IOException(READ_ONLY_EXCEPTION);
	}
}
