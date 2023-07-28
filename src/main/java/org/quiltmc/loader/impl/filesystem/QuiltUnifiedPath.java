package org.quiltmc.loader.impl.filesystem;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltUnifiedPath extends QuiltMapPath<QuiltUnifiedFileSystem, QuiltUnifiedPath> {

	QuiltUnifiedPath(QuiltUnifiedFileSystem fs, @Nullable QuiltUnifiedPath parent, String name) {
		super(fs, parent, name);
	}

	@Override
	QuiltUnifiedPath getThisPath() {
		return this;
	}
}
