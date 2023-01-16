package org.quiltmc.loader.impl.filesystem;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltZipPath extends QuiltBasePath<QuiltZipFileSystem, QuiltZipPath> {

	QuiltZipPath(QuiltZipFileSystem fs, QuiltZipPath parent, String name) {
		super(fs, parent, name);
	}

	@Override
	QuiltZipPath getThisPath() {
		return this;
	}
}
