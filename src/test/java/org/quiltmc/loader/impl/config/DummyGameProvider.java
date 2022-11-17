/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.impl.config;

import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.util.Arguments;

import java.nio.file.Path;
import java.util.Collection;

public class DummyGameProvider implements GameProvider {
	private final Path launchDir;

	public DummyGameProvider(Path launchDir) {
		this.launchDir = launchDir;
	}

	@Override
	public String getGameId() {
		return null;
	}

	@Override
	public String getGameName() {
		return null;
	}

	@Override
	public String getRawGameVersion() {
		return null;
	}

	@Override
	public String getNormalizedGameVersion() {
		return null;
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		return null;
	}

	@Override
	public String getEntrypoint() {
		return null;
	}

	@Override
	public Path getLaunchDirectory() {
		return this.launchDir;
	}

	@Override
	public boolean isObfuscated() {
		return false;
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return false;
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public boolean locateGame(QuiltLauncher launcher, String[] args) {
		return false;
	}

	@Override
	public void initialize(QuiltLauncher launcher) {

	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return null;
	}

	@Override
	public void unlockClassPath(QuiltLauncher launcher) {

	}

	@Override
	public void launch(ClassLoader loader) {

	}

	@Override
	public Arguments getArguments() {
		return new Arguments();
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		return new String[0];
	}
}
