package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
enum RuleType {
	AT_LEAST,
	AT_MOST,
	EXACTLY,
	BETWEEN;
}
