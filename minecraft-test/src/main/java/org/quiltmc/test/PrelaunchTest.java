package org.quiltmc.test;

import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Test preLaunch entrypoint.
 */
public final class PrelaunchTest implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (TestMod.class.getClassLoader() != QuiltLauncherBase.getLauncher().getTargetClassLoader()) {
			throw new IllegalStateException("Invalid class loader: " + TestMod.class.getClassLoader());
		}

		System.out.println("Hello from preLaunch in Quilt");
		System.out.printf("PreLaunch CL: %s%n", TestMod.class.getClassLoader());
	}
}
