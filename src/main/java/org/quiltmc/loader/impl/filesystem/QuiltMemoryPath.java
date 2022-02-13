package org.quiltmc.loader.impl.filesystem;

import org.jetbrains.annotations.NotNull;

public final class QuiltMemoryPath extends QuiltBasePath<QuiltMemoryFileSystem, QuiltMemoryPath> {

	QuiltMemoryPath(@NotNull QuiltMemoryFileSystem fs, QuiltMemoryPath parent, String name) {
		super(fs, parent, name);
	}

	@Override
	@NotNull
	QuiltMemoryPath getThisPath() {
		return this;
	}
}
