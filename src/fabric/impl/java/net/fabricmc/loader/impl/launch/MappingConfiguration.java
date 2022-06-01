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

package net.fabricmc.loader.impl.launch;

import net.fabricmc.mapping.tree.TinyTree;

public final class MappingConfiguration {
	private final org.quiltmc.loader.impl.launch.common.MappingConfiguration delegate;

	public MappingConfiguration(org.quiltmc.loader.impl.launch.common.MappingConfiguration delegate) {
		this.delegate = delegate;
	}

	public String getGameId() {
		return delegate.getGameId();
	}

	public String getGameVersion() {
		return delegate.getGameVersion();
	}

	public boolean matches(String gameId, String gameVersion) {
		return delegate.matches(gameId, gameVersion);
	}

	public TinyTree getMappings() {
		return delegate.getMappings();
	}

	public String getTargetNamespace() {
		return delegate.getTargetNamespace();
	}

	public boolean requiresPackageAccessHack() {
		return delegate.requiresPackageAccessHack();
	}
}
