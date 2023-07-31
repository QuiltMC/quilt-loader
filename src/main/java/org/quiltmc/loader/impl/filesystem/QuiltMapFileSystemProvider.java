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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
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
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderReadOnly;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class QuiltMapFileSystemProvider<FS extends QuiltMapFileSystem<FS, P>, P extends QuiltMapPath<FS, P>> extends FileSystemProvider {

	static final String READ_ONLY_EXCEPTION = "This FileSystem is read-only";

	protected abstract QuiltFSP<FS> quiltFSP();

	protected abstract Class<FS> fileSystemClass();

	protected abstract Class<P> pathClass();

	@Override
	public FileSystem getFileSystem(URI uri) {
		return quiltFSP().getFileSystem(uri);
	}

	protected P toAbsolutePath(Path path) {
		Class<P> pathClass = pathClass();
		if (!pathClass.isInstance(path)) {
			throw new IllegalArgumentException("The given path is not " + pathClass);
		}

		return pathClass.cast(path).toAbsolutePath().normalize();
	}

	@Override
	public P getPath(URI uri) {
		return quiltFSP().getFileSystem(uri).root.resolve(uri.getPath());
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		P p = toAbsolutePath(path);
		QuiltUnifiedEntry entry = p.fs.getEntry(p);
		if (entry instanceof QuiltUnifiedFile) {
			return ((QuiltUnifiedFile) entry).createInputStream();
		} else if (entry != null) {
			throw new FileSystemException("Cannot open an InputStream on a directory!");
		} else {
			throw new NoSuchFileException(path.toString());
		}
	}

	@Override
	public OutputStream newOutputStream(Path pathIn, OpenOption... options) throws IOException {
		if (options.length == 0) {
			options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE };
		}

		boolean append = false;
		boolean truncate = false;
		boolean create = false;
		boolean deleteOnClose = false;

		P path = toAbsolutePath(pathIn);

		for (OpenOption option : options) {
			if (option instanceof LinkOption) {
				// Okay
				continue;
			} else if (option instanceof StandardOpenOption) {
				switch ((StandardOpenOption) option) {
					case APPEND:{
						if (truncate) {
							throw new IllegalArgumentException("Cannot append and truncate! " + options);
						} else {
							append = true;
						}
						break;
					}
					case TRUNCATE_EXISTING: {
						if (append) {
							throw new IllegalArgumentException("Cannot append and truncate! " + options);
						} else {
							truncate = true;
						}
						break;
					}
					case CREATE: {
						create = true;
						break;
					}
					case CREATE_NEW: {
						if (path.fs.exists(path)) {
							throw new IOException(path + " already exists, and CREATE_NEW is specified!");
						}
						create = true;
						break;
					}
					case DELETE_ON_CLOSE: {
						deleteOnClose = true;
						break;
					}
					case READ: {
						throw new UnsupportedOperationException("Cannot open an OutputStream with StandardOpenOption.READ!");
					}
					case WRITE:
						break;
					case DSYNC:
					case SPARSE:
					case SYNC:
					default:
						break;
				}
			} else {
				throw new UnsupportedOperationException("Unknown option " + option);
			}
		}

		ensureWriteable(path);
		QuiltUnifiedEntry current = path.fs.getEntry(path);
		QuiltUnifiedFile targetFile = null;

		if (create) {
			if (current != null) {
				delete(path);
			}

			path.fs.addEntryRequiringParent(targetFile = new QuiltMemoryFile.ReadWrite(path));
		} else if (current instanceof QuiltUnifiedFile) {
			targetFile = (QuiltUnifiedFile) current;
		} else {
			throw new IOException("Cannot open an OutputStream on " + current);
		}

		OutputStream stream = targetFile.createOutputStream(append, truncate);

		if (deleteOnClose) {
			final OutputStream previous = stream;
			stream = new OutputStream() {

				@Override
				public void write(int b) throws IOException {
					previous.write(b);
				}

				@Override
				public void write(byte[] b) throws IOException {
					previous.write(b);
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					previous.write(b, off, len);
				}

				@Override
				public void flush() throws IOException {
					previous.flush();
				}

				@Override
				public void close() throws IOException {
					previous.close();
					delete(path);
				}
			};
		}

		return stream;
	}

	protected void ensureWriteable(P path) throws IOException {
		QuiltUnifiedEntry rootEntry = path.fs.getEntry(path.fs.root);
		if (rootEntry == null) {
			// Empty, so it's constructing
			return;
		}

		if (rootEntry instanceof QuiltUnifiedFolderWriteable) {
			return;
		} else if (rootEntry instanceof QuiltUnifiedFolderReadOnly) {
			throw new IOException(READ_ONLY_EXCEPTION);
		} else {
			throw new IllegalStateException("Unexpected root " + rootEntry);
		}
	}

	@Override
	public SeekableByteChannel newByteChannel(Path pathIn, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
		throws IOException {

		P path = toAbsolutePath(pathIn);
		QuiltUnifiedEntry entry = path.fs.getEntry(path);

		if (!path.getFileSystem().isReadOnly()) {
			boolean create = false;
			for (OpenOption o : options) {
				if (o == StandardOpenOption.CREATE_NEW) {
					if (entry != null) {
						throw new IOException("File already exists: " + path);
					}
					create = true;
				} else if (o == StandardOpenOption.CREATE) {
					create = true;
				}
			}

			if (create && entry == null) {
				if (!path.isAbsolute()) {
					throw new IOException("Cannot work above the root!");
				}
				path.fs.addEntryRequiringParent(entry = new QuiltMemoryFile.ReadWrite(path));
			}
		}

		if (entry instanceof QuiltUnifiedFile) {
			return ((QuiltUnifiedFile) entry).createByteChannel(options);
		} else if (entry != null) {
			throw new FileSystemException("Cannot open a ByteChannel on a directory!");
		} else {
			throw new NoSuchFileException(path.toString());
		}
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		P qmp = toAbsolutePath(dir);

		final QuiltMapPath<?, ?>[] entries;
		QuiltUnifiedEntry entry = qmp.fs.getEntry(qmp);
		if (entry instanceof QuiltUnifiedFolderReadOnly) {
			entries = ((QuiltUnifiedFolderReadOnly) entry).children;
		} else if (entry instanceof QuiltUnifiedFolderWriteable) {
			entries = ((QuiltUnifiedFolderWriteable) entry).children
					.toArray(new QuiltMapPath[0]);
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

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		P path = toAbsolutePath(dir);
		if (path.fs.exists(path)) {
			throw new FileAlreadyExistsException(path.toString());
		}
		ensureWriteable(path);
		path.fs.addEntryRequiringParent(new QuiltUnifiedFolderWriteable(path));
	}

	@Override
	public void delete(Path path) throws IOException {
		delete0(path, true);
	}

	@Override
	public boolean deleteIfExists(Path path) throws IOException {
		return delete0(path, false);
	}

	private boolean delete0(Path path, boolean throwIfMissing) throws IOException {
		P p = toAbsolutePath(path);
		ensureWriteable(p);
		return p.fs.removeEntry(p, throwIfMissing);
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		P src = toAbsolutePath(source);
		P dst = toAbsolutePath(target);

		if (src.equals(dst)) {
			return;
		}

		copy0(src, dst, false, options);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		P src = toAbsolutePath(source);
		P dst = toAbsolutePath(target);

		if (src.equals(dst)) {
			return;
		}

		copy0(src, dst, true, options);
	}

	private void copy0(P src, P dst, boolean isMove, CopyOption[] options) throws IOException {
		if (isMove) {
			ensureWriteable(src);
		}
		ensureWriteable(dst);

		QuiltUnifiedEntry srcEntry = src.fs.getEntry(src);
		QuiltUnifiedEntry dstEntry = dst.fs.getEntry(dst);

		if (srcEntry == null) {
			throw new NoSuchFileException(src.toString());
		}

		if (!(srcEntry instanceof QuiltUnifiedFile)) {
			throw new IOException("Not a file: " + src);
		}

		QuiltUnifiedFile srcFile = (QuiltUnifiedFile) srcEntry;
		boolean canExist = false;

		for (CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				canExist = true;
			}
		}

		if (canExist) {
			delete(dst);
		} else if (dstEntry != null) {
			throw new FileAlreadyExistsException(dst.toString());
		}

		if (isMove) {
			dstEntry = srcEntry.createMovedTo(dst);
		} else {
			dstEntry = srcEntry.createCopiedTo(dst);
		}

		dst.fs.addEntryRequiringParent(dstEntry);
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
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		P p = toAbsolutePath(path);
		for (AccessMode mode : modes) {
			if (mode == AccessMode.WRITE) {
				ensureWriteable(p);
			}
		}
		QuiltUnifiedEntry entry = p.fs.getEntry(p);
		if (entry == null) {
			throw new NoSuchFileException(p.toString());
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		P qmp = toAbsolutePath(path);
		if (type == BasicFileAttributeView.class) {
			BasicFileAttributeView view = new BasicFileAttributeView() {
				@Override
				public String name() {
					return "basic";
				}

				@Override
				public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
					throws IOException {
					// Unsupported
					// Since we don't need to throw we won't
				}

				@Override
				public BasicFileAttributes readAttributes() throws IOException {
					return QuiltMapFileSystemProvider.this.readAttributes(qmp, BasicFileAttributes.class, options);
				}
			};
			return type.cast(view);
		}

		return null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
		throws IOException {

		if (type == BasicFileAttributes.class) {
			P p = toAbsolutePath(path);
			QuiltUnifiedEntry entry = p.fs.getEntry(p);
			if (entry == null) {
				if ("/".equals(path.toString())) {
					throw new NoSuchFileException(p.fs.getClass() + " [root]");
				}
				throw new NoSuchFileException(path.toString());
			}
			return type.cast(entry.createAttributes());
		} else {
			throw new UnsupportedOperationException("Unsupported attributes " + type);
		}
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return quiltFSP().readAttributes(this, path, attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new IOException("Attributes are unmodifiable");
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return toAbsolutePath(path).fs.getFileStores().iterator().next();
	}
}
