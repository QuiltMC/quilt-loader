package org.quiltmc.loader.impl;

import java.nio.file.Path;

import org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl;

public class QuiltPluginManagerForTests extends QuiltPluginManagerImpl {

	public QuiltPluginManagerForTests(Path modsDir) {
		super(modsDir, null, true, new QuiltLoaderConfig());
	}

}
