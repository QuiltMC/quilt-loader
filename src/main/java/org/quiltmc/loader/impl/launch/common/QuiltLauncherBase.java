/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.launch.common;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import org.quiltmc.loader.impl.FormattedException;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltGuiEntry;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.mixin.MixinEnvironment;

public abstract class QuiltLauncherBase implements QuiltLauncher {
	public static Path minecraftJar;

	private static boolean mixinReady;
	private static Map<String, Object> properties;
	private static QuiltLauncher launcher;
	private static MappingConfiguration mappingConfiguration = new MappingConfiguration();

	protected QuiltLauncherBase() {
		setLauncher(this);
	}

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return mappingConfiguration;
	}

	protected static void setProperties(Map<String, Object> propertiesA) {
		if (properties != null && properties != propertiesA) {
			throw new RuntimeException("Duplicate setProperties call!");
		}

		properties = propertiesA;
	}

	private static void setLauncher(QuiltLauncher launcherA) {
		if (launcher != null && launcher != launcherA) {
			throw new RuntimeException("Duplicate setLauncher call!");
		}

		launcher = launcherA;
	}

	public static QuiltLauncher getLauncher() {
		return launcher;
	}

	public static Map<String, Object> getProperties() {
		return properties;
	}

	protected static void handleFormattedException(FormattedException exc) {
		Throwable actualExc = exc.getMessage() != null ? exc : exc.getCause();
		Log.error(LogCategory.GENERAL, exc.getMainText(), actualExc);

		GameProvider gameProvider = QuiltLoaderImpl.INSTANCE.tryGetGameProvider();

		if (gameProvider == null || !gameProvider.displayCrash(actualExc, exc.getMainText())) {
			QuiltGuiEntry.displayError(exc.getMainText(), actualExc, false, true);
		} else {
			System.exit(1);
		}

		throw new AssertionError("exited");
	}

	protected static void setupUncaughtExceptionHandler() {
		Thread mainThread = Thread.currentThread();
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			try {
				if (e instanceof FormattedException) {
					handleFormattedException((FormattedException) e);
				} else {
					String mainText = String.format("Uncaught exception in thread \"%s\"", t.getName());
					Log.error(LogCategory.GENERAL, mainText, e);

					GameProvider gameProvider = QuiltLoaderImpl.INSTANCE.tryGetGameProvider();

					if (Thread.currentThread() == mainThread
							&& (gameProvider == null || !gameProvider.displayCrash(e, mainText))) {
						QuiltGuiEntry.displayError(mainText, e, false, false);
					}
				}
			} catch (Throwable e2) { // just in case
				e.addSuppressed(e2);
				e.printStackTrace();
			}
		});
	}

	protected static void finishMixinBootstrapping() {
		if (mixinReady) {
			throw new RuntimeException("Must not call QuiltLauncherBase.finishMixinBootstrapping() twice!");
		}

		try {
			Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
			m.setAccessible(true);
			m.invoke(null, MixinEnvironment.Phase.INIT);
			m.invoke(null, MixinEnvironment.Phase.DEFAULT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		mixinReady = true;
	}

	public static boolean isMixinReady() {
		return mixinReady;
	}
}
