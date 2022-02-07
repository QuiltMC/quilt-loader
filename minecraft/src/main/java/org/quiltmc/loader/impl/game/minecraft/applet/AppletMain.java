package org.quiltmc.loader.impl.game.minecraft.applet;

import java.io.File;

public final class AppletMain {
	private AppletMain() { }

	public static File hookGameDir(File file) {
		File proposed = AppletLauncher.gameDir;

		if (proposed != null) {
			return proposed;
		} else {
			return file;
		}
	}

	public static void main(String[] args) {
		AppletFrame me = new AppletFrame("Minecraft", null);
		me.launch(args);
	}
}
