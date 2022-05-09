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

package org.quiltmc.loader.api.config;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.config.util.ConfigsImpl;

public final class Configs {
	private Configs() {}

	/**
	 * @return all registered config files
	 */
	public static Iterable<Config> getAll() {
		return ConfigsImpl.getAll();
	}

	/**
	 * @return all registered config files for the given mod
	 */
	public static Iterable<Config> getConfigs(String familyId) {
		return ConfigsImpl.getConfigs(familyId);
	}

	/**
	 * @return the specified config, if it is registered
	 */
	public static @Nullable Config getConfig(String familyId, String configId) {
		return ConfigsImpl.getConfig(familyId, configId);
	}
}
