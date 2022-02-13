package org.quiltmc.loader.impl.filesystem;

public final class QuiltJoinedPath extends QuiltBasePath<QuiltJoinedFileSystem, QuiltJoinedPath> {

	QuiltJoinedPath(QuiltJoinedFileSystem fs, QuiltJoinedPath parent, String name) {
		super(fs, parent, name);
	}

	@Override
	QuiltJoinedPath getThisPath() {
		return this;
	}
}
