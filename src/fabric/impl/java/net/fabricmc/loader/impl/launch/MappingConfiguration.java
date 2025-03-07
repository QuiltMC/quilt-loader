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

package net.fabricmc.loader.impl.launch;

import org.quiltmc.loader.impl.game.MappingConfigurationImpl;

@Deprecated
public final class MappingConfiguration {
	private final org.quiltmc.loader.impl.game.MappingConfiguration delegate;

	public MappingConfiguration(org.quiltmc.loader.impl.game.MappingConfiguration delegate) {
		this.delegate = delegate;
	}

	private MappingConfigurationImpl cast() {
		if (delegate instanceof MappingConfigurationImpl) {
			return (MappingConfigurationImpl) delegate;
		} else {
			throw new UnsupportedOperationException("Fabric MappingConfiguration is not supported on game providers other than Minecraft!");
		}
	}
	public String getGameId() {
		return cast().getGameId();
	}

	public String getGameVersion() {
		return cast().getGameVersion();
	}

	public boolean matches(String gameId, String gameVersion) {
		return cast().matches(gameId, gameVersion);
	}


	public String getTargetNamespace() {
		return cast().getTargetNamespace();
	}

	public boolean requiresPackageAccessHack() {
		return cast().requiresPackageAccessHack();
	}
}
