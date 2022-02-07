package org.quiltmc.loader.impl.game.minecraft.applet;

/**
 * PLEASE NOTE:
 *
 * <p>This class is originally copyrighted under Apache License 2.0
 * by the MCUpdater project (https://github.com/MCUpdater/MCU-Launcher/).
 *
 * <p>It has been adapted here for the purposes of the Fabric loader.
 */
class AppletForcedShutdownListener implements Runnable {
	private final long duration;

	AppletForcedShutdownListener(long duration) {
		this.duration = duration;
	}

	@Override
	public void run() {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}

		System.out.println("~~~ Forcing exit! ~~~");
		System.exit(0);
	}
}
