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

import net.fabricmc.api.EnvType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.Arguments;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public abstract class QuiltLauncherBase implements QuiltLauncher {
	public static Path minecraftJar;

	protected static Logger LOGGER = LogManager.getFormatterLogger("FabricLoader");
	private static boolean mixinReady;
	private static Map<String, Object> properties;
	private static QuiltLauncher launcher;
	private static MappingConfiguration mappingConfiguration = new MappingConfiguration();

	protected QuiltLauncherBase() {
		setLauncher(this);
	}

	public static File getLaunchDirectory(Arguments argMap) {
		return new File(argMap.getOrDefault("gameDir", "."));
	}

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return mappingConfiguration;
	}

	private static boolean emittedInfo = false;

	protected static Path deobfuscate(String gameId, String gameVersion, Path gameDir, Path jarFile, QuiltLauncher launcher) {
		if (!Files.exists(jarFile)) {
			throw new RuntimeException("Could not locate Minecraft: " + jarFile + " not found");
		}

		LOGGER.debug("Requesting deobfuscation of " + jarFile.getFileName());

		if (!launcher.isDevelopment()) { // in-dev is already deobfuscated
			Path deobfJarDir = gameDir.resolve(".fabric").resolve("remappedJars");

			if (!gameId.isEmpty()) {
				String versionedId = gameVersion.isEmpty() ? gameId : String.format("%s-%s", gameId, gameVersion);
				deobfJarDir = deobfJarDir.resolve(versionedId);
			}

			String targetNamespace = mappingConfiguration.getTargetNamespace();
			// TODO: allow versioning mappings?
			String deobfJarFilename = targetNamespace + "-" + jarFile.getFileName();
			Path deobfJarFile = deobfJarDir.resolve(deobfJarFilename);
			Path deobfJarFileTmp = deobfJarDir.resolve(deobfJarFilename + ".tmp");

			if (Files.exists(deobfJarFileTmp)) { // previous unfinished remap attempt
				LOGGER.warn("Incomplete remapped file found! This means that the remapping process failed on the previous launch. If this persists, make sure to let us at Quilt know!");

				try {
					Files.deleteIfExists(deobfJarFile);
					Files.deleteIfExists(deobfJarFileTmp);
				} catch (IOException e) {
					throw new RuntimeException("can't delete incompletely remapped files", e);
				}
			}

			TinyTree mappings;

			if (!Files.exists(deobfJarFile)
					&& (mappings = mappingConfiguration.getMappings()) != null
					&& mappings.getMetadata().getNamespaces().contains(targetNamespace)) {
				LOGGER.debug("Quilt mapping file detected, applying...");

				if (!emittedInfo) {
					LOGGER.info("Quilt is preparing JARs on first launch, this may take a few seconds...");
					emittedInfo = true;
				}

				try {
					deobfuscate0(jarFile, deobfJarFile, deobfJarFileTmp, mappings, targetNamespace);
				} catch (IOException e) {
					throw new RuntimeException("error remapping game jar "+jarFile, e);
				}
			}

			jarFile = deobfJarFile;
		}

		try {
			launcher.propose(UrlUtil.asUrl(jarFile));
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}

		if (minecraftJar == null) {
			minecraftJar = jarFile;
		}

		return jarFile;
	}

	private static void deobfuscate0(Path jarFile, Path deobfJarFile, Path deobfJarFileTmp, TinyTree mappings, String targetNamespace) throws IOException {
		Files.createDirectories(deobfJarFile.getParent());

		boolean found;

		do {
			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(TinyRemapperMappingsHelper.create(mappings, "official", targetNamespace))
					.rebuildSourceFilenames(true)
					.build();

			Set<Path> depPaths = new HashSet<>();

			for (URL url : launcher.getLoadTimeDependencies()) {
				try {
					Path path = UrlUtil.asPath(url);
					if (!Files.exists(path)) {
						throw new RuntimeException("Path does not exist: " + path);
					}

					if (!path.equals(jarFile)) {
						depPaths.add(path);
					}
				} catch (UrlConversionException e) {
					throw new RuntimeException("Failed to convert '" + url + "' to path!", e);
				}
			}

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(deobfJarFileTmp)
					// force jar despite the .tmp extension
					.assumeArchive(true)
					// don't accept class names from a blacklist of dependencies that Quilt itself utilizes
					// TODO: really could use a better solution, as always...
					.filter(clsName -> !clsName.startsWith("com/google/common/")
							&& !clsName.startsWith("com/google/gson/")
							&& !clsName.startsWith("com/google/thirdparty/")
							&& !clsName.startsWith("org/apache/logging/log4j/")
							&& !clsName.startsWith("org/quiltmc/json5"))
					.build()) {
				for (Path path : depPaths) {
					LOGGER.debug("Appending '" + path + "' to remapper classpath");
					remapper.readClassPath(path);
				}
				remapper.readInputs(jarFile);
				remapper.apply(outputConsumer);
			} finally {
				remapper.finish();
			}

			// Minecraft doesn't tend to check if a ZipFileSystem is already present,
			// so we clean up here.

			depPaths.add(deobfJarFileTmp);
			for (Path p : depPaths) {
				try {
					p.getFileSystem().close();
				} catch (Exception e) {
					// pass
				}

				try {
					FileSystems.getFileSystem(new URI("jar:" + p.toUri())).close();
				} catch (Exception e) {
					// pass
				}
			}

			try (JarFile jar = new JarFile(deobfJarFileTmp.toFile())) {
				found = jar.stream().anyMatch((e) -> e.getName().endsWith(".class"));
			}

			if (!found) {
				LOGGER.error("Generated deobfuscated JAR contains no classes! Trying again...");
				Files.delete(deobfJarFileTmp);
			} else {
				Files.move(deobfJarFileTmp, deobfJarFile);
			}
		} while (!found);

		if (!Files.exists(deobfJarFile)) {
			throw new RuntimeException("Remapped .JAR file does not exist after remapping! Cannot continue!");
		}
	}

	public static void processArgumentMap(Arguments argMap, EnvType envType) {
		switch (envType) {
			case CLIENT:
				if (!argMap.containsKey("accessToken")) {
					argMap.put("accessToken", "QuiltMC");
				}

				String version = System.getProperty(SystemProperties.LAUNCHER_NAME);
				if (version == null) {
					if ((version = argMap.get("version")) == null) {
						version = "Unknown";
						LOGGER.error("Launcher version unknown! Please provide it by setting the system property " + SystemProperties.LAUNCHER_NAME);
					}
				}
				argMap.put("version", version);

				String versionType = "";
				if(argMap.containsKey("versionType") && !argMap.get("versionType").equalsIgnoreCase("release")){
					versionType = argMap.get("versionType") + "/";
				}
				argMap.put("versionType", versionType + "Quilt Loader");

				if (!argMap.containsKey("gameDir")) {
					argMap.put("gameDir", getLaunchDirectory(argMap).getAbsolutePath());
				}
				break;
			case SERVER:
				argMap.remove("version");
				argMap.remove("gameDir");
				argMap.remove("assetsDir");
				break;
		}
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

	protected static void finishMixinBootstrapping() {
		if (mixinReady) {
			throw new RuntimeException("Must not call FabricLauncherBase.finishMixinBootstrapping() twice!");
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
