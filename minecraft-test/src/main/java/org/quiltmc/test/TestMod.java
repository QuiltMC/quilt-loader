package org.quiltmc.test;

import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;

import net.fabricmc.api.ModInitializer;

/**
 * Test entrypoint for a mod.
 */
public final class TestMod implements ModInitializer {
	@Override
	public void onInitialize() {
		if (TestMod.class.getClassLoader() != QuiltLauncherBase.getLauncher().getTargetClassLoader()) {
			throw new IllegalStateException("Invalid class loader: " + TestMod.class.getClassLoader());
		}

		System.out.println("Hello from Quilt");
		System.out.printf("Main CL: %s%n", TestMod.class.getClassLoader());
	}
}
