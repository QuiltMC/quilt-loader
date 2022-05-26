package org.quiltmc.loader.impl.config;

import org.quiltmc.loader.api.config.WrappedConfig;
import org.quiltmc.loader.api.config.values.ValueList;

public class TestValueListConfig extends WrappedConfig {
	public final String test = "watermark";
	public final int thingy = 1009;
	public final ValueList<String> strings = ValueList.create("");
}
