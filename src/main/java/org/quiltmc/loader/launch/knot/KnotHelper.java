package org.quiltmc.loader.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.launch.knot.Knot;

/**
 * This class infects the stacktrace, so that all stacktraces can be identified as coming from
 * Quilt Loader.
 */
public final class KnotHelper {
	public static void launchClient(String[] args) {
		Knot.launch(args, EnvType.CLIENT);
	}

	public static void launchServer(String[] args) {
		Knot.launch(args, EnvType.SERVER);
	}
}
