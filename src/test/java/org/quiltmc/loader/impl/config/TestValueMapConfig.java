package org.quiltmc.loader.impl.config;

import org.quiltmc.loader.api.config.values.ValueMap;

public class TestValueMapConfig {
	public final int version = 100;
	public final String flavor = "lemon";
	public final ValueMap<Integer> weights = ValueMap.builder(0).build();
}
