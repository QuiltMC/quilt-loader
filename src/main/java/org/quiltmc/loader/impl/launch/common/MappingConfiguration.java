/*
 * Copyright 2022, 2023 QuiltMC
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.zip.ZipError;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;

import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.ManifestUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

import org.quiltmc.loader.impl.util.mappings.FilteringMappingVisitor;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class MappingConfiguration {
	private boolean initialized;

	private String gameId;
	private String gameVersion;
	private String mappingsSource;
	private final VisitableMappingTree mappings = new MemoryMappingTree();
	private List<String> namespaces;

	public String getGameId() {
		initialize();

		return gameId;
	}

	public String getGameVersion() {
		initialize();

		return gameVersion;
	}

	public String getMappingsSource() {
		initialize();

		return mappingsSource;
	}

	public boolean matches(String gameId, String gameVersion) {
		initialize();

		return (this.gameId == null || gameId == null || gameId.equals(this.gameId))
				&& (this.gameVersion == null || gameVersion == null || gameVersion.equals(this.gameVersion));
	}

	public MappingTreeView getMappings() {
		initialize();

		return mappings;
	}

	public List<String> getNamespaces() {
		return namespaces;
	}

	public String getTargetNamespace() {
		GameProvider gameProvider = QuiltLoaderImpl.INSTANCE.tryGetGameProvider();
		if (gameProvider != null)
			return gameProvider.getNamespace();
		// else
		// If the game provider doesn't exist yet, use the development flag to set the namespace
		return QuiltLauncherBase.getLauncher().isDevelopment() ? "named" : "intermediary";
	}

	public boolean requiresPackageAccessHack() {
		// TODO
		return getTargetNamespace().equals("named");
	}


	private void initialize() {
		if (initialized) return;

		// Load named/intermediary
		Enumeration<URL> urls;
		try {
			urls = MappingConfiguration.class.getClassLoader().getResources("mappings/mappings.tiny");
		} catch (IOException e) {
			throw new UncheckedIOException("Error trying to locate mappings", e);
		}

		boolean read = false;
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			Log.info(LogCategory.MAPPINGS, "Loading mappings: %s", url);

			try {
				URLConnection connection = url.openConnection();

				if (connection instanceof JarURLConnection) {
					Manifest manifest = ((JarURLConnection) connection).getManifest();

					if (manifest != null) {
						gameId = ManifestUtil.getManifestValue(manifest, new Name("Game-Id"));
						gameVersion = ManifestUtil.getManifestValue(manifest, new Name("Game-Version"));
					}
				}

				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					long time = System.currentTimeMillis();
					MappingFormat format = readMappingFormat(reader);
					reader.mark(8192*2); // seems to read 2x the buffer size
					FilteringMappingVisitor filter = new FilteringMappingVisitor(mappings);
					switch (format) {
						case TINY_FILE:
							if (!Tiny1FileReader.getNamespaces(reader).contains(getTargetNamespace())) {
								Log.info(LogCategory.MAPPINGS, "Skipping mappings: Missing namespace '%s'", getTargetNamespace());
								continue;
							}
							reader.reset();
							Tiny1FileReader.read(reader, filter);
							break;
						case TINY_2_FILE:
							if (!Tiny2FileReader.getNamespaces(reader).contains(getTargetNamespace())) {
								Log.info(LogCategory.MAPPINGS, "Skipping mappings: Missing namespace '%s'", getTargetNamespace());
								continue;
							}
							reader.reset();
							Tiny2FileReader.read(reader, filter);
							break;
						default:
							throw new UnsupportedOperationException("Unsupported mapping format " + format);
					}

					this.mappingsSource = url.toString();
					read = true;
					Log.debug(LogCategory.MAPPINGS, "Loading mappings took %d ms", System.currentTimeMillis() - time);
					break;
                }
			} catch (IOException | ZipError e) {
				throw new RuntimeException("Error reading "+url, e);
			}
		}

		if (!read) {
			if (QuiltLoader.isDevelopmentEnvironment()) {
				Log.error(LogCategory.MAPPINGS, "Unable to locate named mappings");
			} else {
				Log.error(LogCategory.MAPPINGS, "Unable to locate intermediary! Something is very wrong.");
			}
		}

		// Load mojmap
		String mojmapPath = System.getProperty(SystemProperties.MOJMAP_PATH);
		if (mojmapPath != null) {
			try (BufferedReader reader = Files.newBufferedReader(Paths.get(mojmapPath))) {
				ProGuardFileReader.read(reader, "mojang", "official", new MappingSourceNsSwitch(mappings, "official"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		this.namespaces = new ArrayList<>();
		namespaces.add(mappings.getSrcNamespace());
		namespaces.addAll(mappings.getDstNamespaces());
		this.namespaces = Collections.unmodifiableList(namespaces);
		initialized = true;
	}

	private MappingFormat readMappingFormat(BufferedReader reader) throws IOException {
		// We will only ever need to read tiny here
		// so to strip the other formats from the included copy of mapping IO, don't use MappingReader.read()
		reader.mark(4096);
		final MappingFormat format = MappingReader.detectFormat(reader);
		reader.reset();

		return format;
	}
}
