package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolder;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class QuiltMapFileSystem<FS extends QuiltMapFileSystem<FS, P>, P extends QuiltMapPath<FS, P>>
	extends QuiltBaseFileSystem<FS, P>
	implements CachedFileSystem {

	private final Map<P, QuiltUnifiedEntry> entries;

	public QuiltMapFileSystem(Class<FS> filesystemClass, Class<P> pathClass, String name, boolean uniqueify) {
		super(filesystemClass, pathClass, name, uniqueify);
		this.entries = startWithConcurrentMap() ? new ConcurrentHashMap<>() : new HashMap<>();
	}

	// Construction

	protected abstract boolean startWithConcurrentMap();

	// Subtype helpers

	protected void switchToReadOnly() {
		for (Map.Entry<P, QuiltUnifiedEntry> entry : entries.entrySet()) {
			entry.setValue(entry.getValue().switchToReadOnly());
		}
	}

	// File map access

	protected int getEntryCount() {
		return entries.size();
	}

	protected Iterable<P> getEntryPathIterator() {
		return entries.keySet();
	}

	protected QuiltUnifiedEntry getEntry(Path path) {
		if (path.getFileSystem() != this) {
			throw new IllegalStateException("The given path is for a different filesystem!");
		}
		return entries.get(path.toAbsolutePath().normalize());
	}

	protected void addEntryRequiringParent(QuiltUnifiedEntry newEntry) throws IOException {
		addEntryRequiringParents0(newEntry, IOException::new);
	}

	protected void addEntryRequiringParentUnsafe(QuiltUnifiedEntry newEntry) {
		addEntryRequiringParents0(newEntry, IllegalStateException::new);
	}

	private <T extends Throwable> void addEntryRequiringParents0(QuiltUnifiedEntry newEntry, Function<String, T> execCtor) throws T {
		P path = pathClass.cast(newEntry.path);
		P parent = path.parent;
		if (parent == null) {
			if (root.equals(path)) {
				addEntryWithoutParents(newEntry, execCtor);
				return;
			} else {
				throw new IllegalArgumentException("Somehow obtained a normalised, absolute, path without a parent which isn't root? " + path);
			}
		}

		QuiltUnifiedEntry entry = getEntry(parent);
		if (entry instanceof QuiltUnifiedFolderWriteable) {
			addEntryWithoutParents(newEntry, execCtor);
			((QuiltUnifiedFolderWriteable) entry).children.add(path);
		} else if (entry == null) {
			throw execCtor.apply("Cannot put entry " + path + " because the parent folder doesn't exist!");
		} else {
			throw execCtor.apply("Cannot put entry " + path + " because the parent is not a folder (was " + entry + ")");
		}
	}

	protected void addEntryAndParents(QuiltUnifiedEntry newEntry) throws IOException {
		addEntryAndParents0(newEntry, IOException::new);
	}

	protected void addEntryAndParentsUnsafe(QuiltUnifiedEntry newEntry) {
		addEntryAndParents0(newEntry, IllegalStateException::new);
	}

	private <T extends Throwable> void addEntryAndParents0(QuiltUnifiedEntry newEntry, Function<String, T> execCtor) throws T {
		P path = addEntryWithoutParents0(newEntry, execCtor);
		P parent = path;
		P previous = path;
		while ((parent = parent.getParent()) != null) {
			if (isDirectory(parent)) {
				break;
			}
			QuiltUnifiedEntry parentEntry = getEntry(parent);
			QuiltUnifiedFolderWriteable parentFolder;
			if (parentEntry == null) {
				entries.put(parent, parentFolder = new QuiltUnifiedFolderWriteable(parent));
			} else if (parentEntry instanceof QuiltUnifiedFolderWriteable) {
				parentFolder = (QuiltUnifiedFolderWriteable) parentEntry;
			} else {
				throw execCtor.apply(
					"Cannot make a file into a folder " + parent + " for " + path + ", currently " + parentEntry
				);
			}

			parentFolder.children.add(previous);

			previous = parent;
		}
	}

	protected <T extends Throwable> void addEntryWithoutParents(QuiltUnifiedEntry newEntry, Function<String, T> execCtor) throws T {
		addEntryWithoutParents0(newEntry, execCtor);
	}

	private <T extends Throwable> P addEntryWithoutParents0(QuiltUnifiedEntry newEntry, Function<String, T> execCtor) throws T {
		if (newEntry.path.fs != this) {
			throw new IllegalArgumentException("The given entry is for a different filesystem!");
		}
		P path = pathClass.cast(newEntry.path);
		QuiltUnifiedEntry current = entries.putIfAbsent(path, newEntry);
		if (current == null) {
			return path;
		} else {
			throw execCtor.apply("Cannot replace existing entry " + current + " with " + newEntry);
		}
	}

	protected boolean removeEntry(P path, boolean throwIfMissing) throws IOException {
		path = path.toAbsolutePath().normalize();
		QuiltUnifiedEntry current = getEntry(path);
		if (current == null) {
			if (throwIfMissing) {
				throw new IOException("Cannot remove an entry if it doesn't exist! " + path);
			} else {
				return false;
			}
		}

		if (current instanceof QuiltUnifiedFolder) {
			if (!((QuiltUnifiedFolder) current).getChildren().isEmpty()) {
				throw new DirectoryNotEmptyException("Cannot remove a non-empty folder!");
			}
		}

		QuiltUnifiedEntry parent = getEntry(path.parent);
		entries.remove(path);
		if (parent instanceof QuiltUnifiedFolderWriteable) {
			((QuiltUnifiedFolderWriteable) parent).children.remove(path);
		}
		return true;
	}

	// General filesystem operations

	@Override
	public boolean isDirectory(Path path, LinkOption... options) {
		return getEntry(path) instanceof QuiltUnifiedFolder;
	}

	@Override
	public boolean isRegularFile(Path path, LinkOption[] options) {
		return getEntry(path) instanceof QuiltUnifiedFile;
	}

	@Override
	public boolean exists(Path path, LinkOption... options) {
		return getEntry(path) != null;
	}

	@Override
	public boolean notExists(Path path, LinkOption... options) {
		// Nothing can go wrong, so this is fine
		return !exists(path, options);
	}

	@Override
	public boolean isReadable(Path path) {
		return exists(path);
	}

	@Override
	public boolean isExecutable(Path path) {
		return exists(path);
	}

	@Override
	public boolean isSymbolicLink(Path path) {
		// Completely unsupported
		return false;
	}

	@Override
	public Collection<? extends Path> getChildren(Path dir) throws IOException {
		QuiltUnifiedEntry entry = getEntry(dir);
		if (!(entry instanceof QuiltUnifiedFolder)) {
			throw new NotDirectoryException(dir.toString());
		}
		return Collections.unmodifiableCollection(((QuiltUnifiedFolder) entry).getChildren());
	}

	@Override
	public Stream<Path> list(Path dir) throws IOException {
		return getChildren(dir).stream().map(p -> (Path) p);
	}

	@Override
	public Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
		dir = dir.toAbsolutePath().normalize();
		Deque<Path> stack = new ArrayDeque<>();
		Path p = dir;
		do {
			if (isDirectory(p)) {
				break;
			} else {
				stack.push(p);
			}
		} while ((p = p.getParent()) != null);

		while (!stack.isEmpty()) {
			provider().createDirectory(stack.pop(), attrs);
		}

		return dir;
	}
}
