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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@SuppressWarnings("unchecked") // TODO make more specific
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltJoinedFileSystemProvider extends FileSystemProvider {
	public QuiltJoinedFileSystemProvider() {}

	public static final String SCHEME = "quilt.jfs";

	private static final Map<String, WeakReference<QuiltJoinedFileSystem>> ACTIVE_FILESYSTEMS = new HashMap<>();

	static {
		// Java requires we create a class named "Handler"
		// in the package "<system-prop>.<scheme>"
		// See java.net.URL#URL(java.lang.String, java.lang.String, int, java.lang.String)
		final String key = "java.protocol.handler.pkgs";
		final String pkg = "org.quiltmc.loader.impl.filesystem";
		String prop = System.getProperty(key);
		if (prop == null) {
			System.setProperty(key, pkg);
		} else if (!prop.contains(pkg)) {
			System.setProperty(key, prop + "|" + pkg);
		}
	}

	synchronized static void register(QuiltJoinedFileSystem fs) {
		URI uri = URI.create(SCHEME + "://" + fs.name + "/hello");
		if (!"/hello".equals(uri.getPath())) {
			throw new IllegalArgumentException("Not a valid name - it includes a path! '" + fs.name + "'");
		}
		WeakReference<QuiltJoinedFileSystem> oldRef = ACTIVE_FILESYSTEMS.get(fs.name);
		if (oldRef != null) {
			QuiltJoinedFileSystem old = oldRef.get();
			if (old != null) {
				throw new IllegalStateException("Multiple registered file systems for name '" + fs.name + "'");
			}
		}
		ACTIVE_FILESYSTEMS.put(fs.name, new WeakReference<>(fs));
	}

	@Nullable
	synchronized static QuiltJoinedFileSystem getFileSystem(String name) {
		WeakReference<QuiltJoinedFileSystem> ref = ACTIVE_FILESYSTEMS.get(name);
		return ref != null ? ref.get() : null;
	}

	static synchronized void closeFileSystem(QuiltJoinedFileSystem fs) {
		WeakReference<QuiltJoinedFileSystem> removedRef = ACTIVE_FILESYSTEMS.remove(fs.name);
		if (removedRef.get() != fs) {
			throw new IllegalStateException("FileSystem already removed!");
		}
	}

	public static QuiltJoinedFileSystemProvider instance() {
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider instanceof QuiltJoinedFileSystemProvider) {
				return (QuiltJoinedFileSystemProvider) provider;
			}
		}
		throw new IllegalStateException("Unable to load QuiltJoinedFileSystemProvider via services!");
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
	public QuiltJoinedPath getPath(URI uri) {
		if (!SCHEME.equals(uri.getScheme())) {
			throw new IllegalArgumentException("Wrong scheme! " + uri);
		}
		String authority = uri.getAuthority();
		if (authority == null) {
			authority = uri.getHost();
		} else if (authority.endsWith(":0")) {
			// We add a (useless) port to allow URI.authority and host to be populated
			authority = authority.substring(0, authority.length() - 2);
		}
		WeakReference<QuiltJoinedFileSystem> fsRef;
		synchronized (this) {
			fsRef = ACTIVE_FILESYSTEMS.get(authority);
		}
		QuiltJoinedFileSystem fs;
		if (fsRef == null || (fs = fsRef.get()) == null) {
			throw new IllegalArgumentException("Unknown file system name '" + authority + "'");
		}
		return fs.root.resolve(uri.getPath());
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		if (!(path instanceof QuiltJoinedPath)) {
			throw new IllegalArgumentException("The given path is not a QuiltJoinedPath!");
		}

		QuiltJoinedPath p = (QuiltJoinedPath) path;

		int count = p.fs.getBackingPathCount();
		for (int i = 0; i < count; i++) {
			Path real = p.fs.getBackingPath(i, p);
			try {
				return Files.newInputStream(real, options);
			} catch (NoSuchFileException e) {
				if (i == count - 1) {
					throw e;
				}
			}
		}

		throw new NoSuchFileException(path.toString());
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
		throws IOException {

		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		if (!(path instanceof QuiltJoinedPath)) {
			throw new IllegalArgumentException("The given path is not a QuiltJoinedPath!");
		}

		QuiltJoinedPath p = (QuiltJoinedPath) path;

		int count = p.fs.getBackingPathCount();
		for (int i = 0; i < count; i++) {
			Path real = p.fs.getBackingPath(i, p);
			try {
				return Files.newByteChannel(real, options, attrs);
			} catch (NoSuchFileException e) {
				if (i == count - 1) {
					throw e;
				}
			}
		}

		throw new NoSuchFileException(path.toString());
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		QuiltJoinedPath qmp = (QuiltJoinedPath) dir;
		return new DirectoryStream<Path>() {

			final List<Path> backingPaths = new ArrayList<>();
			final List<DirectoryStream<Path>> streams = new ArrayList<>();
			boolean opened = false;
			boolean closed = false;

			{
				boolean anyReal = false;
				for (int i = 0; i < qmp.fs.getBackingPathCount(); i++) {
					Path backing = qmp.fs.getBackingPath(i, qmp);
					backingPaths.add(backing);
					if (FasterFiles.isDirectory(backing)) {
						streams.add(Files.newDirectoryStream(backing, path -> {
							return filter.accept(toJoinedPath(backing, path));
						}));
						anyReal = true;
					} else {
						streams.add(null);
					}
				}

				if (!anyReal) {
					throw new NotDirectoryException(dir.toString());
				}
			}

			private QuiltJoinedPath toJoinedPath(Path backing, Path path) {
				Path relative = backing.relativize(path);
				QuiltJoinedPath joined;
				if (relative.getFileSystem().getSeparator().equals(qmp.fs.getSeparator())) {
					joined = qmp.resolve(relative.toString());
				} else {
					joined = qmp;
					for (int s = 0; s < relative.getNameCount(); s++) {
						joined = joined.resolve(relative.getName(s).toString());
					}
				}
				return joined;
			}

			@Override
			public void close() throws IOException {
				if (!closed) {
					closed = true;
					IOException exception = null;
					for (DirectoryStream<Path> sub : streams) {
						if (sub == null) continue;
						try {
							sub.close();
						} catch (IOException e) {
							if (exception == null) {
								exception = e;
							} else {
								exception.addSuppressed(e);
							}
						}
					}

					if (exception != null) {
						throw exception;
					}
				}
			}

			@Override
			public Iterator<Path> iterator() {
				if (opened) {
					throw new IllegalStateException("newDirectoryStream only supports a single iteration!");
				}
				opened = true;

				return new Iterator<Path>() {

					int index = -1;
					Iterator<Path> subIterator;
					Path next;
					boolean calledNext = false;

					/** Used to make sure duplicate paths don't get returned. */
					final Set<Path> paths = new HashSet<>();

					@Override
					public Path next() {
						if (!hasNext()) {
							throw new NoSuchElementException();
						}
						calledNext = true;
						return next;
					}

					@Override
					public boolean hasNext() {
						if (closed) {
							return false;
						}

						if (next != null && !calledNext) {
							return true;
						}

						if (index >= streams.size()) {
							return false;
						}

						next = null;
						calledNext = false;

						while (true) {
							try {
								if (subIterator != null && subIterator.hasNext()) {
									next = toJoinedPath(backingPaths.get(index), subIterator.next());
									if (paths.add(next)) {
										return true;
									} else {
										next = null;
										continue;
									}
								}
							} catch (DirectoryIteratorException e) {
								if (e.getCause() instanceof NotDirectoryException) {
									subIterator = null;
								} else {
									throw e;
								}
							}

							index++;

							if (index >= streams.size()) {
								return false;
							}

							DirectoryStream<Path> stream = streams.get(index);
							if (stream != null) {
								subIterator = stream.iterator();
							}
						}
					}
				};
			}
		};
	}

	private static QuiltJoinedPath toAbsQuiltPath(Path path) {
		Path p = path.toAbsolutePath().normalize();
		if (p instanceof QuiltJoinedPath) {
			return (QuiltJoinedPath) p;
		} else {
			throw new IllegalArgumentException("Only 'QuiltJoinedPath' is supported!");
		}
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		throw new IOException("Cannot create new files or directories!");
	}

	@Override
	public void delete(Path path) throws IOException {
		throw new IOException("Cannot remove files!");
	}

	@Override
	public boolean deleteIfExists(Path path) throws IOException {
		throw new IOException("Cannot remove files!");
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		throw new IOException("Cannot create new files or directories!");
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		throw new IOException("Cannot create new files or directories!");
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
		return ((QuiltJoinedPath) path).fs.getFileStores().iterator().next();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		for (AccessMode mode : modes) {
			if (mode == AccessMode.WRITE) {
				throw new IOException("Cannot create new files or directories!");
			}
		}
		QuiltJoinedPath quiltPath = toAbsQuiltPath(path);
		for (int i = 0; i < quiltPath.fs.getBackingPathCount(); i++) {
			Path real = quiltPath.fs.getBackingPath(i, quiltPath);
			try {
				real.getFileSystem().provider().checkAccess(real, modes);
				return;
			} catch (NoSuchFileException | FileNotFoundException e) {
				// Ignored
			}
		}
		throw new NoSuchFileException(quiltPath.toString());
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {

		QuiltJoinedPath quiltPath = toAbsQuiltPath(path);
		for (int i = 0; i < quiltPath.fs.getBackingPathCount(); i++) {
			Path real = quiltPath.fs.getBackingPath(i, quiltPath);
			V view = Files.getFileAttributeView(real, type, options);
			if (view != null) {
				return view;
			}
		}
		return null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
		throws IOException {

		QuiltJoinedPath quiltPath = toAbsQuiltPath(path);
		for (int i = 0; i < quiltPath.fs.getBackingPathCount(); i++) {
			Path real = quiltPath.fs.getBackingPath(i, quiltPath);
			try {
				return Files.readAttributes(real, type, options);
			} catch (NoSuchFileException | FileNotFoundException e) {
				// Ignored
			}
		}

		throw new NoSuchFileException(quiltPath.toString());
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		QuiltJoinedPath quiltPath = toAbsQuiltPath(path);
		for (int i = 0; i < quiltPath.fs.getBackingPathCount(); i++) {
			Path real = quiltPath.fs.getBackingPath(i, quiltPath);
			try {
				return Files.readAttributes(real, attributes, options);
			} catch (NoSuchFileException | FileNotFoundException e) {
				// Ignored
			}
		}

		throw new NoSuchFileException(quiltPath.toString());
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new IOException("Cannot set attributes!");
	}
}
