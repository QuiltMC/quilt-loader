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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFile;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolder;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFolderWriteable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class QuiltMapFileSystem<FS extends QuiltMapFileSystem<FS, P>, P extends QuiltMapPath<FS, P>>
	extends QuiltBaseFileSystem<FS, P>
	implements CachedFileSystem {

	/** Controls {@link #dumpEntries(String)}. */
	private static final boolean ENABLE_DEBUG_DUMPING = Boolean.getBoolean(SystemProperties.DEBUG_DUMP_FILESYSTEM_CONTENTS);

	/** Controls {@link #validate()}. */
	private static final boolean ENABLE_VALIDATION = Boolean.getBoolean(SystemProperties.DEBUG_VALIDATE_FILESYSTEM_CONTENTS);

	private final Map<P, QuiltUnifiedEntry> entries;

	public QuiltMapFileSystem(Class<FS> filesystemClass, Class<P> pathClass, String name, boolean uniqueify) {
		super(filesystemClass, pathClass, name, uniqueify);
		this.entries = startWithConcurrentMap() ? new ConcurrentHashMap<>() : new HashMap<>();
	}

	public static void dumpEntries(FileSystem fs, String name) {
		if (ENABLE_DEBUG_DUMPING && fs instanceof QuiltMapFileSystem) {
			((QuiltMapFileSystem<?, ?>) fs).dumpEntries(name);
		}
	}

	public void dumpEntries(String name) {
		if (!ENABLE_DEBUG_DUMPING) {
			return;
		}
		try (BufferedWriter bw = Files.newBufferedWriter(Paths.get("dbg-map-fs-" + name + ".txt"))) {
			Set<String> paths = new TreeSet<>();
			for (Map.Entry<P, QuiltUnifiedEntry> entry : entries.entrySet()) {
				paths.add(entry.getKey().toString() + "  = " + entry.getValue().getClass());
			}
			for (String key : paths) {
				bw.append(key);
				bw.newLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void validate() {
		if (!ENABLE_VALIDATION) {
			return;
		}
		for (Entry<P, QuiltUnifiedEntry> entry : entries.entrySet()) {
			P path = entry.getKey();
			QuiltUnifiedEntry e = entry.getValue();
			if (!path.isRoot()) {
				QuiltUnifiedEntry parent = entries.get(path.parent);
				if (parent == null || !(parent instanceof QuiltUnifiedFolder)) {
					throw new IllegalStateException("Entry " + path + " doesn't have a parent!");
				}
				QuiltUnifiedFolder pp = (QuiltUnifiedFolder) parent;
				if (!pp.getChildren().contains(path)) {
					throw new IllegalStateException("Entry " + path + " isn't linked to from its parent!");
				}
			}
		}
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
				addEntryWithoutParents0(newEntry, execCtor);
				return;
			} else {
				throw new IllegalArgumentException("Somehow obtained a normalised, absolute, path without a parent which isn't root? " + path);
			}
		}

		QuiltUnifiedEntry entry = getEntry(parent);
		if (entry instanceof QuiltUnifiedFolderWriteable) {
			addEntryWithoutParents0(newEntry, execCtor);
			((QuiltUnifiedFolderWriteable) entry).children.add(path);
		} else if (entry == null) {
			throw execCtor.apply("Cannot put entry " + path + " because the parent folder doesn't exist!");
		} else {
			throw execCtor.apply("Cannot put entry " + path + " because the parent is not a folder (was " + entry + ")");
		}

		validate();
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
			QuiltUnifiedEntry parentEntry = getEntry(parent);
			QuiltUnifiedFolderWriteable parentFolder;
			if (parentEntry == null) {
				addEntryWithoutParents0(parentFolder = new QuiltUnifiedFolderWriteable(parent), execCtor);
			} else if (parentEntry instanceof QuiltUnifiedFolderWriteable) {
				parentFolder = (QuiltUnifiedFolderWriteable) parentEntry;
			} else {
				throw execCtor.apply(
					"Cannot make a file into a folder " + parent + " for " + path + ", currently " + parentEntry
				);
			}

			if (!parentFolder.children.add(previous)) {
				break;
			}

			previous = parent;
		}

		validate();
	}

	protected void addEntryWithoutParentsUnsafe(QuiltUnifiedEntry newEntry) {
		addEntryWithoutParents0(newEntry, IllegalStateException::new);
	}

	protected void addEntryWithoutParents(QuiltUnifiedEntry newEntry) throws IOException {
		addEntryWithoutParents0(newEntry, IOException::new);
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
		if ("/quilt_tags/quilt_tags.accesswidener".equals(path.toString())) {
			System.out.println("Removing " + path);
		}
		QuiltUnifiedEntry current = getEntry(path);
		if (current == null) {
			if (throwIfMissing) {
				List<P> keys = new ArrayList<>(entries.keySet());
				Collections.sort(keys);
				for (P key : keys) {
					System.out.println(key + " = " + getEntry(key).getClass());
				}
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
		// (File mounts aren't technically symbolic links)
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

		validate();

		return dir;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		FileStore store = new FileStore() {
			@Override
			public String type() {
				return QuiltMapFileSystem.this.getClass().getName();
			}

			@Override
			public boolean supportsFileAttributeView(String name) {
				return "basic".equals(name);
			}

			@Override
			public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
				return type == BasicFileAttributeView.class;
			}

			@Override
			public String name() {
				return QuiltMapFileSystem.this.name;
			}

			@Override
			public boolean isReadOnly() {
				return QuiltMapFileSystem.this.isReadOnly();
			}

			@Override
			public long getUsableSpace() throws IOException {
				return 10;
			}

			@Override
			public long getUnallocatedSpace() throws IOException {
				return 0;
			}

			@Override
			public long getTotalSpace() throws IOException {
				return getUsableSpace();
			}

			@Override
			public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
				return null;
			}

			@Override
			public Object getAttribute(String attribute) throws IOException {
				return null;
			}
		};
		return Collections.singleton(store);
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return Collections.singleton("basic");
	}
}
