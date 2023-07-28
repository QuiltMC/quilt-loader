package org.quiltmc.loader.impl.filesystem;

import org.jetbrains.annotations.Nullable;

public abstract class QuiltMapPath<FS extends QuiltMapFileSystem<FS, P>, P extends QuiltMapPath<FS, P>>
	extends QuiltBasePath<FS, P> {

	QuiltMapPath(FS fs, @Nullable P parent, String name) {
		super(fs, parent, name);
	}

}
