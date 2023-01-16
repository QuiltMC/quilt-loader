package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.QuiltZipEntry;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.QuiltZipFile;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.QuiltZipFolder;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltZipFileSystemProvider extends FileSystemProvider {

	public static final String SCHEME = "quilt.zfs";
	static final String READ_ONLY_EXCEPTION = "This FileSystem is read-only";
	static final QuiltFSP<QuiltZipFileSystem> PROVIDER = new QuiltFSP<>(SCHEME);

	public static QuiltZipFileSystemProvider instance() {
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider instanceof QuiltZipFileSystemProvider) {
				return (QuiltZipFileSystemProvider) provider;
			}
		}
		throw new IllegalStateException("Unable to load QuiltZipFileSystemProvider via services!");
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
	public QuiltZipPath getPath(URI uri) {
		return PROVIDER.getFileSystem(uri).root.resolve(uri.getPath());
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
		throws IOException {
		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		if (path instanceof QuiltZipPath) {
			QuiltZipPath p = (QuiltZipPath) path;
			QuiltZipEntry entry = p.fs.entries.get(p.toAbsolutePath());
			if (entry instanceof QuiltZipFolder) {
				throw new FileSystemException("Cannot open an InputStream on a directory!");
			} else if (entry instanceof QuiltZipFile) {
				return ((QuiltZipFile) entry).createByteChannel();
			} else {
				throw new NoSuchFileException(path.toString());
			}
		} else {
			throw new IllegalArgumentException("The given path is not a QuiltZipPath!");
		}
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		for (OpenOption o : options) {
			if (o != StandardOpenOption.READ) {
				throw new UnsupportedOperationException("'" + o + "' not allowed");
			}
		}

		if (path instanceof QuiltZipPath) {
			QuiltZipPath p = (QuiltZipPath) path;
			QuiltZipEntry entry = p.fs.entries.get(p.toAbsolutePath());
			if (entry instanceof QuiltZipFolder) {
				throw new FileSystemException("Cannot open an InputStream on a directory!");
			} else if (entry instanceof QuiltZipFile) {
				return ((QuiltZipFile) entry).createInputStream();
			} else {
				throw new NoSuchFileException(path.toString());
			}
		} else {
			throw new IllegalArgumentException("The given path is not a QuiltZipPath!");
		}
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		QuiltZipPath from = toAbsQuiltPath(dir);

		QuiltZipEntry entry = from.fs.entries.get(from);
		final Map<String, QuiltZipPath> entries;
		if (entry instanceof QuiltZipFolder) {
			entries = ((QuiltZipFolder) entry).children;
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
					final Iterator<QuiltZipPath> entryIter = entries.values().iterator();

					@Override
					public boolean hasNext() {
						return entryIter.hasNext();
					}

					@Override
					public Path next() {
						return entryIter.next();
					}
				};
			}
		};
	}

	private static QuiltZipPath toAbsQuiltPath(Path path) {
		Path p = path.toAbsolutePath().normalize();
		if (p instanceof QuiltZipPath) {
			return (QuiltZipPath) p;
		} else {
			throw new IllegalArgumentException("Only 'QuiltZipPath' is supported!");
		}
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		throw new IOException(READ_ONLY_EXCEPTION);
	}

	@Override
	public void delete(Path path) throws IOException {
		throw new IOException(READ_ONLY_EXCEPTION);
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		throw new IOException(READ_ONLY_EXCEPTION);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		throw new IOException(READ_ONLY_EXCEPTION);
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
		return ((QuiltZipPath) path).fs.getFileStores().iterator().next();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		for (AccessMode mode : modes) {
			if (mode == AccessMode.WRITE) {
				throw new IOException(READ_ONLY_EXCEPTION);
			}
		}
		QuiltZipPath quiltPath = toAbsQuiltPath(path);
		QuiltZipEntry entry = quiltPath.fs.entries.get(quiltPath);
		if (entry == null) {
			throw new NoSuchFileException(quiltPath.toString());
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		if (type == BasicFileAttributeView.class) {
			BasicFileAttributeView view = new BasicFileAttributeView() {
				@Override
				public String name() {
					return "basic";
				}

				@Override
				public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
					// Unsupported
					// Since we don't need to throw we won't
				}

				@Override
				public BasicFileAttributes readAttributes() throws IOException {
					return QuiltZipFileSystemProvider.this.readAttributes(path, BasicFileAttributes.class, options);
				}
			};
			return type.cast(view);
		}
		return null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
		throws IOException {

		if (type != BasicFileAttributes.class) {
			throw new UnsupportedOperationException("Unsupported attributes " + type);
		}

		QuiltZipPath zipPath = toAbsQuiltPath(path);
		QuiltZipEntry entry = zipPath.fs.entries.get(zipPath);
		if (entry != null) {
			return type.cast(entry.createAttributes(zipPath));
		} else {
			throw new NoSuchFileException(zipPath.toString());
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
