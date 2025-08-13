package org.quiltmc.loader.impl.filesystem;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public enum ZipHandling {
	PLAIN,
	JAR;
}
