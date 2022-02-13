package org.quiltmc.loader.impl.filesystem.memory;

import java.nio.file.attribute.BasicFileAttributes;

abstract class QuiltMemoryEntry {

	final QuiltMemoryPath path;

	public QuiltMemoryEntry(QuiltMemoryPath path) {
		this.path = path;
	}

	protected abstract BasicFileAttributes createAttributes();

	@Override
	public String toString() {
		return path + " " + getClass().getSimpleName();
	}
}
