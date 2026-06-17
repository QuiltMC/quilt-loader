/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.game;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.transformer.PackageAccessFixer;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;

import org.quiltmc.loader.impl.util.Arguments;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public interface GameProvider {
	String getGameId();
	String getGameName();
	String getRawGameVersion();
	String getNormalizedGameVersion();
	Collection<BuiltinMod> getBuiltinMods();

	String getEntrypoint();
	Path getLaunchDirectory();

	/**
	 * The mapping configuration for the game. Unobfuscated games should use {@link EmptyMappingConfiguration}.
	 */
	MappingConfiguration getMappingConfiguration();

	/** @return True if the game itself (identified by {@link #getGameId()}) should be fully access-widend by
	 *         {@link PackageAccessFixer}. This is normally only useful if your mappings moves classes between packages,
	 *         and doesn't widen private members. */
	default boolean requiresPackageAccessFix() {
		// Compatibility code - we need this to always run
		MappingConfiguration mappingConfig = getMappingConfiguration();
		if (mappingConfig instanceof MappingConfigurationImpl) {
			return ((MappingConfigurationImpl) mappingConfig).requiresPackageAccessHack();
		}
		return false;
	}

	boolean requiresUrlClassLoader();

	boolean isEnabled();
	boolean locateGame(QuiltLauncher launcher, String[] args);
	void initialize(QuiltLauncher launcher);
	GameTransformer getEntrypointTransformer();
	void unlockClassPath(QuiltLauncher launcher);
	void launch(ClassLoader loader);

	/**
	 * Returns the game jars mapped to the given namespace.
	 * @param namespace if null, returns the default (mapped) jars
	 * @return a list of all game jars, or null if they do not exist (or, in development environments,
	 * are not known to the gameprovider -- see {@link org.quiltmc.loader.impl.util.SystemProperties#REMAP_CLASSPATH_FILE})
	 */
	@Nullable List<Path> getGameJars(@Nullable String namespace);
	default boolean isGameClass(String name) {
		return true;
	}

	default boolean displayCrash(Throwable exception, String context) {
		return false;
	}

	Arguments getArguments();
	String[] getLaunchArguments(boolean sanitize);

	default boolean canOpenGui() {
		return true;
	}

	default boolean hasAwtSupport() {
		return LoaderUtil.hasAwtSupport();
	}

	class BuiltinMod {
		public BuiltinMod(List<Path> paths, InternalModMetadata metadata) {
			Objects.requireNonNull(paths, "null paths");
			Objects.requireNonNull(metadata, "null metadata");

			this.paths = paths;
			this.metadata = metadata;
		}

		public final List<Path> paths;
		public final InternalModMetadata metadata;
	}
}
