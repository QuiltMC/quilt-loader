package org.quiltmc.loader.impl.memfilesys;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
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

public final class QuiltMemoryPath implements Path {

	static final String NAME_ROOT = "/";
	static final String NAME_SELF = ".";
	static final String NAME_PARENT = "..";

	final QuiltMemoryFileSystem fs;
	final QuiltMemoryPath parent;

	/** The {@link String} name of this path. For efficiency we set this to one of {@link #NAME_ROOT},
	 * {@link #NAME_PARENT}, or {@link #NAME_SELF} if it matches. */
	final String name;

	final boolean absolute;
	final int nameCount;

	final int hash;

	QuiltMemoryPath(QuiltMemoryFileSystem fs, QuiltMemoryPath parent, String name) {
		this.fs = fs;
		this.parent = parent;

		if (NAME_ROOT.equals(name)) {
			if (parent != null) {
				throw new IllegalArgumentException("Root paths cannot have a parent!");
			}
			this.name = NAME_ROOT;
			this.nameCount = 0;
			this.absolute = true;
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

			if (parent == null) {
				absolute = false;
			} else {
				absolute = parent.absolute;
			}
		}

		this.hash = fs.hashCode() * 31 + (parent == null ? name.hashCode() : (parent.hash * 31 + name.hashCode()));
	}

	private boolean isRoot() {
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
		if (!(obj instanceof QuiltMemoryPath)) {
			return false;
		}
		QuiltMemoryPath o = (QuiltMemoryPath) obj;
		return fs == o.fs && nameCount == o.nameCount && name.equals(o.name) && Objects.equals(parent, o.parent);
	}

	@Override
	public FileSystem getFileSystem() {
		return fs;
	}

	@Override
	public boolean isAbsolute() {
		return absolute;
	}

	@Override
	public QuiltMemoryPath getRoot() {
		if (isAbsolute()) {
			return fs.root;
		} else {
			return null;
		}
	}

	@Override
	public QuiltMemoryPath getFileName() {
		if (name.isEmpty()) {
			return null;
		} else if (parent == null) {
			return this;
		} else {
			return new QuiltMemoryPath(fs, null, name);
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

		QuiltMemoryPath p = this;
		QuiltMemoryPath upper;

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
	public QuiltMemoryPath getParent() {
		return parent;
	}

	@Override
	public int getNameCount() {
		return nameCount;
	}

	@Override
	public QuiltMemoryPath getName(int index) {
		if (index < 0 || index >= nameCount) {
			throw new IllegalArgumentException("index out of bounds");
		}

		if (index == 0 && parent == null) {
			return this;
		}
		if (index == nameCount - 1) {
			return getFileName();
		}

		QuiltMemoryPath p = this;
		for (int i = index + 1; i < nameCount; i++) {
			p = p.parent;
		}

		return p.getFileName();
	}

	@Override
	public QuiltMemoryPath subpath(int beginIndex, int endIndex) {
		if (beginIndex < 0) {
			throw new IllegalArgumentException("beginIndex < 0!");
		}

		if (endIndex > nameCount) {
			throw new IllegalArgumentException("endIndex > getNameCount()!");
		}

		QuiltMemoryPath end = this;

		for (int i = nameCount; i > endIndex; i--) {
			end = end.parent;
		}

		QuiltMemoryPath from = end;

		for (int i = endIndex - 1; i > beginIndex; i--) {
			from = from.parent;
		}

		String fromS = from.name;
		if (fromS.startsWith("/")) {
			fromS = fromS.substring(1);
		}

		QuiltMemoryPath path = new QuiltMemoryPath(fs, null, fromS);
		List<String> names = end.names();
		names = names.subList(beginIndex + 1, names.size());

		for (String sub : names) {
			path = path.resolve(sub);
		}

		return path;
	}

	@Override
	public boolean startsWith(Path other) {
		if (other instanceof QuiltMemoryPath) {
			QuiltMemoryPath o = (QuiltMemoryPath) other;
			if (absolute != o.absolute) {
				return false;
			}
			if (o.nameCount > nameCount) {
				return false;
			}

			// TODO: Optimise this!

			QuiltMemoryPath p = this;

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
		if (other instanceof QuiltMemoryPath) {
			QuiltMemoryPath o = (QuiltMemoryPath) other;
			QuiltMemoryPath t = this;

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
	public QuiltMemoryPath normalize() {
		if (NAME_SELF.equals(name)) {
			if (parent != null) {
				return parent.normalize();
			}
			return this;
		}
		if (NAME_PARENT.equals(name)) {
			if (parent != null && parent.parent != null) {
				return parent.parent.normalize();
			}
			return this;
		}

		if (parent == null) {
			return this;
		}

		QuiltMemoryPath p = parent.normalize();
		if (p == parent) {
			return this;
		} else {
			return p.resolve(name);
		}
	}

	@Override
	public QuiltMemoryPath resolve(Path other) {
		if (other.isAbsolute()) {
			return (QuiltMemoryPath) other;
		}

		if (other.getNameCount() == 0) {
			return this;
		}
		QuiltMemoryPath o = (QuiltMemoryPath) other;

		Deque<QuiltMemoryPath> stack = new ArrayDeque<>();

		do {
			stack.push(o);
		} while ((o = o.parent) != null);

		QuiltMemoryPath p = this;

		while (!stack.isEmpty()) {
			p = new QuiltMemoryPath(fs, p, stack.pop().name);
		}

		return p;
	}

	@Override
	public QuiltMemoryPath resolve(String other) {
		QuiltMemoryPath p = this;
		for (String s : other.split("/")) {
			if (!s.isEmpty()) {
				p = new QuiltMemoryPath(fs, p, s);
			}
		}
		return p;
	}

	@Override
	public QuiltMemoryPath resolveSibling(Path other) {
		if (other.isAbsolute() || parent == null) {
			return (QuiltMemoryPath) other;
		}
		return parent.resolve(other);
	}

	@Override
	public QuiltMemoryPath resolveSibling(String other) {
		return resolveSibling(fs.getPath(other));
	}

	@Override
	public QuiltMemoryPath relativize(Path other) {
		if (!(other instanceof QuiltMemoryPath)) {
			throw new IllegalArgumentException("You can only relativize paths from the same provider!");
		}
		QuiltMemoryPath o = (QuiltMemoryPath) other;
		if (o.equals(this)) {
			return new QuiltMemoryPath(fs, null, "");
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

		QuiltMemoryPath path = null;
		for (int j = i; j < names.size(); j++) {
			if (path == null) {
				path = new QuiltMemoryPath(fs, null, NAME_PARENT);
			} else {
				path = path.resolve(NAME_PARENT);
			}
		}

		for (int j = i; j < oNames.size(); j++) {
			if (path == null) {
				path = new QuiltMemoryPath(fs, null, oNames.get(j));
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
			return new URI(QuiltMemoryFileSystemProvider.SCHEME, fs.name, toString(), null);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public QuiltMemoryPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return fs.root.resolve(this);
	}

	@Override
	public QuiltMemoryPath toRealPath(LinkOption... options) throws IOException {
		return toAbsolutePath();
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException("Only the default FileSystem supports 'Path.toFile()'");
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException();
	}

	private List<String> names() {
		if (parent == null) {
			if (isRoot()) {
				return Collections.emptyList();
			} else {
				return Collections.singletonList(name);
			}
		}
		List<String> list = new ArrayList<>(nameCount);
		QuiltMemoryPath p = this;
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
		QuiltMemoryPath p = this;
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
}
