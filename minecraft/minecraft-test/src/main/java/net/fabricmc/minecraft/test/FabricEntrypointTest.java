package net.fabricmc.minecraft.test;

import net.fabricmc.api.ModInitializer;

public class FabricEntrypointTest implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("testmod initialized!");
	}
}
