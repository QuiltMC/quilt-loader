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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public abstract class QuiltBasePath<FS extends QuiltBaseFileSystem<FS, P>, P extends QuiltBasePath<FS, P>>
	implements Path {

	static final String NAME_ROOT = "/";
	static final String NAME_SELF = ".";
	static final String NAME_PARENT = "..";

	final @NotNull FS fs;
	final @Nullable P parent;

	/** The {@link String} name of this path. For efficiency we set this to one of {@link #NAME_ROOT},
	 * {@link #NAME_PARENT}, or {@link #NAME_SELF} if it matches. */
	final String name;

	/** {@link #isAbsolute()} */
	final boolean absolute;

	/** If true then {@link #normalize()} will return this. */
	final boolean normalized;
	final int nameCount;

	final int hash;

	QuiltBasePath(FS fs, @Nullable P parent, String name) {
		Objects.requireNonNull(fs, "filesystem");
		Objects.requireNonNull(name, "name");

		this.fs = fs;
		this.parent = parent;

		if (NAME_ROOT.equals(name)) {
			if (parent != null) {
				throw new IllegalArgumentException("Root paths cannot have a parent!");
			}
			this.name = NAME_ROOT;
			this.nameCount = 0;
			this.absolute = true;
			this.normalized = true;
		} else {
			if (name.equals(NAME_PARENT)) {
				this.name = NAME_PARENT;
			} else if (name.equals(NAME_SELF)) {
				this.name = NAME_SELF;
			} else {
				this.name = name;
			}
			int count = 0;
			if (parent != null) {
				count += parent.nameCount;
			}
			if (!name.isEmpty()) {
				count++;
			}
			this.nameCount = count;

			boolean isNormalName = !name.equals(NAME_PARENT) && !name.equals(NAME_SELF);

			if (parent == null) {
				absolute = false;
				normalized = isNormalName;
			} else {
				absolute = parent.absolute;
				normalized = parent.normalized && isNormalName;
			}
		}

		this.hash = fs.hashCode() * 31 + (parent == null ? name.hashCode() : (parent.hash * 31 + name.hashCode()));
	}

	@Override
	@Nullable
	public P getParent() {
		return parent;
	}

	protected boolean isRoot() {
		return NAME_ROOT == name;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null) return false;
		if (obj.getClass() != getClass()) {
			return false;
		}
		QuiltBasePath<?, ?> o = (QuiltBasePath<?, ?>) obj;
		return fs == o.fs && nameCount == o.nameCount && name.equals(o.name) && Objects.equals(parent, o.parent);
	}

	@Override
	public FS getFileSystem() {
		return fs;
	}

	@Override
	public boolean isAbsolute() {
		return absolute;
	}

	@Override
	@Nullable
	public P getRoot() {
		if (isAbsolute()) {
			return fs.root;
		} else {
			return null;
		}
	}

	/** Safely casts this path to "P" */
	abstract P getThisPath();

	@Override
	@Nullable
	public P getFileName() {
		if (name.isEmpty()) {
			return null;
		} else if (parent == null) {
			return getThisPath();
		} else {
			return fs.createPath(null, name);
		}
	}

	@Override
	public String toString() {
		if (isRoot()) {
			return NAME_ROOT;
		}

		StringBuilder sb = new StringBuilder();

		if (!name.isEmpty()) {
			sb.append(name);
		}

		P p = getThisPath();
		P upper;

		while (true) {
			upper = p.getParent();

			if (upper == null) {
				break;
			}
			sb.insert(0, '/');

			if (upper.name.length() > 0 && !upper.isRoot()) {
				sb.insert(0, upper.name);
			}

			p = upper;
		}

		return sb.toString();
	}

	@Override
	public int getNameCount() {
		return nameCount;
	}

	@Override
	public P getName(int index) {
		if (index < 0 || index >= nameCount) {
			throw new IllegalArgumentException("index out of bounds");
		}

		if (index == 0 && parent == null) {
			return getThisPath();
		}
		if (index == nameCount - 1) {
			return fs.createPath(null, name);
		}

		P p = getThisPath();
		for (int i = index + 1; i < nameCount; i++) {
			p = p.parent;
		}

		return fs.createPath(null, p.name);
	}

	@Override
	public P subpath(int beginIndex, int endIndex) {
		if (beginIndex < 0) {
			throw new IllegalArgumentException("beginIndex < 0!");
		}

		if (endIndex > nameCount) {
			throw new IllegalArgumentException("endIndex > getNameCount()!");
		}

		P end = getThisPath();

		for (int i = nameCount; i > endIndex; i--) {
			end = end.parent;
		}

		P from = end;

		for (int i = endIndex - 1; i > beginIndex; i--) {
			from = from.parent;
		}

		String fromS = from.name;
		if (fromS.startsWith("/")) {
			fromS = fromS.substring(1);
		}

		P path = fs.createPath(null, fromS);
		List<String> names = end.names();
		names = names.subList(beginIndex + 1, names.size());

		for (String sub : names) {
			path = path.resolve(sub);
		}

		return path;
	}

	@Override
	public boolean startsWith(Path other) {
		if (fs.pathClass.isInstance(other)) {
			P o = fs.pathClass.cast(other);
			if (absolute != o.absolute) {
				return false;
			}
			if (o.nameCount > nameCount) {
				return false;
			}

			// TODO: Optimise this!

			P p = getThisPath();

			do {
				if (other.equals(p)) {
					return true;
				}
			} while ((p = p.parent) != null);

			return false;

		} else {
			return false;
		}
	}

	@Override
	public boolean startsWith(String other) {
		return startsWith(fs.getPath(other));
	}

	@Override
	public boolean endsWith(Path other) {
		if (fs.pathClass.isInstance(other)) {
			P o = fs.pathClass.cast(other);
			P t = getThisPath();

			while (o != null && t != null) {
				if (!t.name.equals(o.name)) {
					return false;
				}

				o = o.parent;
				t = t.parent;
			}

			return o == null;
		} else {
			return false;
		}
	}

	@Override
	public boolean endsWith(String other) {
		return endsWith(fs.getPath(other));
	}

	@Override
	public P normalize() {
		if (normalized) {
			return getThisPath();
		}

		if (NAME_SELF.equals(name)) {
			if (parent != null) {
				return parent.normalize();
			}
			return getThisPath();
		}
		if (NAME_PARENT.equals(name)) {
			P path = getThisPath();
			if (parent == null) {
				return path;
			}
			P p = parent.normalize();
			if (NAME_PARENT.equals(p.name)) {
				// ../..
				// Since the parent is also ".." then it must be .. all the way to the root
				// so we can't normalise any further
				return p.resolve(name);
			} else if (p.parent != null) {
				return p.parent;
			} else {
				return fs.createPath(null, NAME_SELF);
			}
		}

		if (parent == null) {
			return getThisPath();
		}

		P p = parent.normalize();
		if (p == parent) {
			return getThisPath();
		} else {
			return p.resolve(name);
		}
	}

	@Override
	public P resolve(Path other) {
		if (other.isAbsolute()) {
			return fs.pathClass.cast(other);
		}

		if (other.getNameCount() == 0) {
			return getThisPath();
		}
		P o = fs.pathClass.cast(other);

		Deque<P> stack = new ArrayDeque<>();

		do {
			stack.push(o);
		} while ((o = o.parent) != null);

		P p = getThisPath();

		while (!stack.isEmpty()) {
			p = fs.createPath(p, stack.pop().name);
		}

		return p;
	}

	@Override
	public P resolve(String other) {
		P p = getThisPath();
		for (String s : other.split("/")) {
			if (!s.isEmpty()) {
				p = fs.createPath(p, s);
			}
		}
		return p;
	}

	@Override
	public P resolveSibling(Path other) {
		if (other.isAbsolute() || parent == null) {
			return fs.pathClass.cast(other);
		}
		return parent.resolve(other);
	}

	@Override
	public P resolveSibling(String other) {
		return resolveSibling(fs.getPath(other));
	}

	@Override
	public P relativize(Path other) {
		P o = fs.pathClass.cast(other);
		if (o.equals(this)) {
			return fs.createPath(null, "");
		}

		if (absolute != o.absolute) {
			throw new IllegalArgumentException(
				"You can only relativize paths if they are both absolute, OR both relative - not one and the other!"
			);
		}

		List<String> names = normalize().names();
		List<String> oNames = o.normalize().names();

		int i;
		for (i = 0; i < Math.min(names.size(), oNames.size()); i++) {
			String a = names.get(i);
			String b = oNames.get(i);
			if (!a.equals(b)) {
				break;
			}
		}

		P path = null;
		for (int j = i; j < names.size(); j++) {
			if (path == null) {
				path = fs.createPath(null, NAME_PARENT);
			} else {
				path = path.resolve(NAME_PARENT);
			}
		}

		for (int j = i; j < oNames.size(); j++) {
			if (path == null) {
				path = fs.createPath(null, oNames.get(j));
			} else {
				path = path.resolve(oNames.get(j));
			}
		}

		return path;

	}

	@Override
	public URI toUri() {
		if (!isAbsolute()) {
			return toAbsolutePath().toUri();
		}
		try {
			// Passing as one string ensures that Java goes ahead and normalizes everything for us
			// Adding the port stores the important info in both the authority and host
			return new URI(fs.provider().getScheme() + "://" + fs.name + ":0" + this);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public P toAbsolutePath() {
		if (isAbsolute()) {
			return getThisPath();
		}
		return fs.root.resolve(this);
	}

	@Override
	public P toRealPath(LinkOption... options) throws IOException {
		return toAbsolutePath();
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException("Only the default FileSystem supports 'Path.toFile()', " + getClass() + " '" + this + "' does not!");
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException();
	}

	protected List<String> names() {
		if (parent == null) {
			if (isRoot()) {
				return Collections.emptyList();
			} else {
				return Collections.singletonList(name);
			}
		}
		List<String> list = new ArrayList<>(nameCount);
		P p = getThisPath();
		do {
			if (p.isRoot()) {
				break;
			}
			list.add(p.name);
		} while ((p = p.getParent()) != null);
		Collections.reverse(list);
		return list;
	}

	@Override
	public Iterator<Path> iterator() {
		if (parent == null) {
			if (isRoot()) {
				return Collections.emptyIterator();
			} else {
				return Collections.<Path> singleton(this).iterator();
			}
		}
		List<Path> list = new ArrayList<>(nameCount);
		P p = getThisPath();
		do {
			if (p.isRoot()) {
				break;
			}
			list.add(p.getFileName());
		} while ((p = p.getParent()) != null);
		Collections.reverse(list);
		return list.iterator();
	}

	@Override
	public int compareTo(Path other) {
		return toString().compareTo(other.toString());
	}

	/** Support for opening directories as an input stream - basically
	 * {@link org.quiltmc.loader.impl.filesystem.quilt.mfs.Handler} and
	 * {@link org.quiltmc.loader.impl.filesystem.quilt.jfs.Handler} */
	public InputStream openUrlInputStream() throws IOException {
		if (FasterFiles.isDirectory(this)) {
			return new ByteArrayInputStream("folder".getBytes(StandardCharsets.UTF_8));
		} else {
			return Files.newInputStream(this);
		}
	}
}
