package org.quiltmc.loader.impl.transformer;

import java.nio.file.Path;

import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class TransformCacheResult {
	public final Path transformCacheFolder;
	public final boolean isNewlyGenerated;
	public final QuiltZipPath transformCacheRoot;

	TransformCacheResult(Path transformCacheFolder, boolean isNewlyGenerated, QuiltZipPath transformCacheRoot) {
		this.transformCacheFolder = transformCacheFolder;
		this.isNewlyGenerated = isNewlyGenerated;
		this.transformCacheRoot = transformCacheRoot;
	}
}
