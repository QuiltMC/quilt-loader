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
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

public final class QuiltMemoryPath implements Path {

	static final String NAME_ROOT = "/";
	static final String NAME_SELF = ".";
	static final String NAME_PARENT = "..";

	final QuiltMemoryFileSystem fs;
	final QuiltMemoryPath parent;
	final String name;

	final boolean absolute;
	final int nameCount;

	final int hash;

	QuiltMemoryPath(QuiltMemoryFileSystem fs, QuiltMemoryPath parent, String name) {
		this.fs = fs;
		this.parent = parent;
		this.name = name;

		int count = 0;
		if (parent != null) {
			count += parent.nameCount;
		}
		if (!name.isEmpty()) {
			count++;
		}
		this.nameCount = count;

		if (parent == null) {
			absolute = NAME_ROOT.equals(name);
		} else {
			absolute = parent != null && parent.absolute;
		}

		this.hash = fs.hashCode() * 31 + (parent == null ? name.hashCode() : (parent.hash * 31 + name.hashCode()));
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
		if (NAME_ROOT.equals(name)) {
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

			if (upper.name.length() > 0 && !NAME_ROOT.equals(upper.name)) {
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

		if (absolute && index == 0) {
			return fs.root;
		}
		if (index == nameCount - 1) {
			return getFileName();
		}

		QuiltMemoryPath p = this;
		for (int i = 0; i < index; i++) {
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

		if (beginIndex == 0) {
			return end;
		} else {
			QuiltMemoryPath from = end;

			for (int i = endIndex; i > beginIndex; i--) {
				from = from.parent;
			}

			// TODO: Implement this!
			// TODO: Test this!
		}
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
		return new QuiltWatchKey(this);
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		return new QuiltWatchKey(this);
	}

	@Override
	public Iterator<Path> iterator() {

	}

	@Override
	public int compareTo(Path other) {

	}
}
