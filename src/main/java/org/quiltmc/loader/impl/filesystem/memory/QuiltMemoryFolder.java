package org.quiltmc.loader.impl.filesystem.memory;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class QuiltMemoryFolder extends QuiltMemoryEntry {

	private QuiltMemoryFolder(QuiltMemoryPath path) {
		super(path);
	}

	@Override
	protected BasicFileAttributes createAttributes() {
		return new QuiltFileAttributes(path, QuiltFileAttributes.SIZE_DIRECTORY);
	}

	public static final class ReadOnly extends QuiltMemoryFolder {
		final QuiltMemoryPath[] children;

		public ReadOnly(QuiltMemoryPath path, QuiltMemoryPath[] children) {
			super(path);
			this.children = children;
		}
	}

	public static final class ReadWrite extends QuiltMemoryFolder {
		final Set<QuiltMemoryPath> children = Collections.newSetFromMap(new ConcurrentHashMap<>());

		public ReadWrite(QuiltMemoryPath path) {
			super(path);
		}
	}
}
