/*
 * Copyright 2022 QuiltMC
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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/** A {@link FileSystem} that exposes multiple {@link Path}s in a single */
public class QuiltJoinedFileSystem extends QuiltBaseFileSystem<QuiltJoinedFileSystem, QuiltJoinedPath> {

	private static final Map<String, Integer> uniqueNames = new HashMap<>();

	final Path[] from;
	final boolean[] shouldCloseFroms;
	boolean isOpen = true;

	public QuiltJoinedFileSystem(String name, List<Path> from) {
		this(name, from, null);
	}

	public QuiltJoinedFileSystem(String name, List<Path> from, List<Boolean> shouldClose) {
		super(QuiltJoinedFileSystem.class, QuiltJoinedPath.class, name);
		this.from = from.toArray(new Path[0]);
		this.shouldCloseFroms = new boolean[from.size()];
		for (int i = 0; i < shouldCloseFroms.length; i++) {
			shouldCloseFroms[i] = shouldClose != null && shouldClose.get(i);
		}
		QuiltJoinedFileSystemProvider.register(this);
	}

	public static synchronized String uniqueOf(String name) {
		Integer current = uniqueNames.get(name);
		if (current != null) {
			current++;
		} else {
			current = 0;
		}
		uniqueNames.put(name, current);
		return name + current;
	}

	@Override
	QuiltJoinedPath createPath(@Nullable QuiltJoinedPath parent, String name) {
		return new QuiltJoinedPath(this, parent, name);
	}

	@Override
	public FileSystemProvider provider() {
		return QuiltJoinedFileSystemProvider.instance();
	}

	@Override
	public synchronized void close() throws IOException {
		if (isOpen) {
			isOpen = false;
			QuiltJoinedFileSystemProvider.closeFileSystem(this);
			for (int i = 0; i < from.length; i++) {
				if (shouldCloseFroms[i]) {
					from[i].getFileSystem().close();
				}
			}
		}
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		Set<String> supported = new HashSet<>();
		for (Path path : from) {
			Set<String> set = path.getFileSystem().supportedFileAttributeViews();
			if (supported.isEmpty()) {
				supported.addAll(set);
			} else {
				supported.retainAll(set);
			}
		}
		return supported;
	}

	public int getBackingPathCount() {
		return from.length;
	}

	public Path getBackingPath(int index, QuiltJoinedPath thisPath) {
		Path other = from[index];
		if (getSeparator().equals(other.getFileSystem().getSeparator())) {
			String thisPathStr = thisPath.toString();
			if (thisPathStr.startsWith("/")) {
				thisPathStr = thisPathStr.substring(1);
			}
			return other.resolve(thisPathStr);
		} else {
			for (String segment : thisPath.names()) {
				other = other.resolve(segment);
			}
			return other;
		}
	}
}
