package org.quiltmc.loader.impl.util;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public interface ExceptionConstructor<T extends Throwable> {

	@NotNull
	T construct(String message);
}
