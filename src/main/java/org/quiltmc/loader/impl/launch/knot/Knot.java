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

package org.quiltmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.quiltmc.loader.impl.launch.GameProvider;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.entrypoint.EntrypointUtils;
import org.quiltmc.loader.impl.game.GameProviders;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.common.QuiltMixinBootstrap;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.spongepowered.asm.launch.MixinBootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class Knot extends QuiltLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private KnotClassLoaderInterface classLoader;
	private boolean isDevelopment;
	private EnvType envType;
	private final File gameJarFile;
	private GameProvider provider;

	public Knot(EnvType type, File gameJarFile) {
		this.envType = type;
		this.gameJarFile = gameJarFile;
	}

	protected ClassLoader init(String[] args) {
		setProperties(properties);

		// configure vars
		if (envType == null) {
			String side = System.getProperty(SystemProperties.SIDE);
			if (side == null) {
				throw new RuntimeException("Please specify side or use a dedicated Knot!");
			}

			switch (side.toLowerCase(Locale.ROOT)) {
			case "client":
				envType = EnvType.CLIENT;
				break;
			case "server":
				envType = EnvType.SERVER;
				break;
			default:
				throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
			}
		}

		// TODO: Restore these undocumented features
		// String proposedEntrypoint = System.getProperty("fabric.loader.entrypoint");

		List<GameProvider> providers = GameProviders.create();
		provider = null;

		for (GameProvider p : providers) {
			if (p.locateGame(envType, args, this.getClass().getClassLoader())) {
				provider = p;
				break;
			}
		}

		if (provider != null) {
			LOGGER.info("Loading for game " + provider.name() + " " + provider.rawVersion());
		} else {
			LOGGER.error("Could not find valid game provider!");
			for (GameProvider p : providers) {
				LOGGER.error("- " + p.name());
			}
			throw new RuntimeException("Could not find valid game provider!");
		}

		isDevelopment = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));

		// Setup classloader
		// TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
		classLoader = provider.knotClassLoaderInterface();
		ClassLoader cl = (ClassLoader) classLoader;

		if (provider.obfuscated()) {
			for (Path path : provider.contextJars()) {
				QuiltLauncherBase.deobfuscate(
						provider.id(), provider.normalizedVersion(),
						provider.launchDirectory(),
						path,
						this
						);
			}
		}

		// Locate entrypoints before switching class loaders
		provider.entrypointTransformer().locateEntrypoints(this);

		Thread.currentThread().setContextClassLoader(cl);

		QuiltLoaderImpl loader = QuiltLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		loader.freeze();

		QuiltLoaderImpl.INSTANCE.loadAccessWideners();

		MixinBootstrap.init();
		QuiltMixinBootstrap.init(getEnvironmentType(), loader);
		QuiltLauncherBase.finishMixinBootstrapping();

		classLoader.getDelegate().initializeTransformers();

		EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);

		return cl;
	}

	public void launch(ClassLoader cl) {
		if(this.provider == null) {
			throw new IllegalStateException("Game provider was not initialized! (Knot#init(String[]))");
		}
		provider.launch(cl);
	}

	@Override
	public String getTargetNamespace() {
		// TODO: Won't work outside of Yarn
		return isDevelopment ? "named" : "intermediary";
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		String cmdLineClasspath = System.getProperty("java.class.path");

		return Arrays.stream(cmdLineClasspath.split(File.pathSeparator)).filter((s) -> {
			if (s.equals("*") || s.endsWith(File.separator + "*")) {
				System.err.println("WARNING: Knot does not support wildcard classpath entries: " + s + " - the game may not load properly!");
				return false;
			} else {
				return true;
			}
		}).map((s) -> {
			File file = new File(s);
			if (!file.equals(gameJarFile)) {
				try {
					return (UrlUtil.asUrl(file));
				} catch (UrlConversionException e) {
					LOGGER.debug(e);
					return null;
				}
			} else {
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	@Override
	public void propose(URL url) {
		QuiltLauncherBase.LOGGER.debug("[Knot] Proposed " + url + " to classpath.");
		classLoader.addURL(url);
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return classLoader.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			return classLoader.getResourceAsStream(name, false);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file '" + name + "'!", e);
		}
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return (ClassLoader) classLoader;
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		if (runTransformers) {
			return classLoader.getDelegate().getPreMixinClassByteArray(name, false);
		} else {
			return classLoader.getDelegate().getRawClassByteArray(name, false);
		}
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	@Override
	public String getEntrypoint() {
		return provider.entrypoint();
	}

	public static void main(String[] args) {
		new Knot(null, null).init(args);
	}
}
