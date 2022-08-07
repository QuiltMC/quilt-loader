package org.quiltmc.loader.impl.plugin.quilt;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;

/** A specialised {@link BuiltinModOption} which never has a useful origin hash, usually because it's either unknown or
 * useless to base the transformer hash key off.
 * <p>
 * Currently only the 'java' mod uses this, however architecture, operating system, or hardware related builtin mods
 * would use this too. */
public class SystemModOption extends BuiltinModOption {

	public SystemModOption(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from, Path resourceRoot) {
		super(pluginContext, meta, from, resourceRoot);
	}

	@Override
	public byte[] computeOriginHash() throws IOException {
		return new byte[] { 0, 1, 2, 3 };
	}

	@Override
	public boolean needsChasmTransforming() {
		return false;
	}
}
