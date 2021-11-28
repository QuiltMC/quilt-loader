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

package org.quiltmc.loader.impl.game;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

import net.fabricmc.loader.impl.util.LoaderUtil;

import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import net.fabricmc.loader.api.metadata.ModMetadata;

import org.quiltmc.loader.impl.util.Arguments;

public interface GameProvider {
	String getGameId();
	String getGameName();
	String getRawGameVersion();
	String getNormalizedGameVersion();
	Collection<BuiltinMod> getBuiltinMods();

	String getEntrypoint();
	Path getLaunchDirectory();
	boolean isObfuscated();
	boolean requiresUrlClassLoader();

	boolean isEnabled();
	boolean locateGame(QuiltLauncher launcher, String[] args);
	void initialize(QuiltLauncher launcher);
	GameTransformer getEntrypointTransformer();
	void unlockClassPath(QuiltLauncher launcher);
	void launch(ClassLoader loader);

	default boolean displayCrash(Throwable exception, String context) {
		return false;
	}

	Arguments getArguments();
	String[] getLaunchArguments(boolean sanitize);

	default boolean canOpenErrorGui() {
		return true;
	}

	default boolean hasAwtSupport() {
		return LoaderUtil.hasAwtSupport();
	}

	class BuiltinMod {
		public BuiltinMod(Path path, ModMetadata metadata) {
			Objects.requireNonNull(path, "null path");
			Objects.requireNonNull(metadata, "null metadata");

			this.path = path;
			this.metadata = metadata;
		}

		public final Path path;
		public final ModMetadata metadata;
	}
}
