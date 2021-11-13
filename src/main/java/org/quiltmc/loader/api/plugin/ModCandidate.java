package org.quiltmc.loader.api.plugin;

import java.nio.file.Path;

public interface ModCandidate {

	// TODO: is this strictly necessary for plugin loading?
	// or can this just be produced by a ModLoadOption right at the end for classloading?

	/** @return A {@link Path} to the inside of the mod, to be used for classloading and asset loading. If this
	 *         candidate is for a mod generated from a file that isn't an archive then this should point to that
	 *         file.  */
	Path path();

	FullModMetadata metadata();
}
