package net.fabricmc.loader.entrypoint.minecraft.hooks;

import java.io.File;

import org.quiltmc.loader.impl.game.minecraft.Hooks;

@Deprecated
public class EntrypointClient {
	public static void start(File runDir, Object gameInstance) {
		Hooks.startClient(runDir, gameInstance);
	}
}
