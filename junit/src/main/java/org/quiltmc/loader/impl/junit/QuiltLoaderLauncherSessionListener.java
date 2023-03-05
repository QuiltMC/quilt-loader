package org.quiltmc.loader.impl.junit;

import net.fabricmc.api.EnvType;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.quiltmc.loader.impl.launch.knot.Knot;
import org.quiltmc.loader.impl.util.SystemProperties;

public class QuiltLoaderLauncherSessionListener implements LauncherSessionListener {
	static {
		System.setProperty(SystemProperties.DEVELOPMENT, "true");
		System.setProperty(SystemProperties.UNIT_TEST, "true");
	}

	private final Knot knot;
	private final ClassLoader classLoader;

	private ClassLoader launcherSessionClassLoader;

	public QuiltLoaderLauncherSessionListener() {
		final Thread currentThread = Thread.currentThread();
		final ClassLoader originalClassLoader = currentThread.getContextClassLoader();

		try {
			knot = new Knot(EnvType.CLIENT);
			classLoader = knot.init(new String[]{});
		} finally {
			// Knot.init sets the context class loader, revert it back for now.
			currentThread.setContextClassLoader(originalClassLoader);
		}
	}

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		launcherSessionClassLoader = currentThread.getContextClassLoader();
		currentThread.setContextClassLoader(classLoader);
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		final Thread currentThread = Thread.currentThread();
		currentThread.setContextClassLoader(launcherSessionClassLoader);
	}
}
