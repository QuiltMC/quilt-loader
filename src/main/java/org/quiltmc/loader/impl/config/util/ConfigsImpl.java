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

package org.quiltmc.loader.impl.config.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.impl.util.ImmutableIterable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigsImpl {
	private static final Map<String, Map<String, Config>> CONFIGS = new TreeMap<>();

	public static void put(String modId, Config config) {
		if (CONFIGS.containsKey(modId) && CONFIGS.get(modId).containsKey(config.getId())) {
			throw new RuntimeException("Config '" + modId + ':' + config.getId() + "' already exists");
		}

		CONFIGS.computeIfAbsent(modId, id -> new TreeMap<>()).put(config.getId(), config);
	}

	public static Iterable<Config> getAll() {
		return ConfigsImpl::itr;
	}

	public static Iterable<Config> getConfigs(String modId) {
		return new ImmutableIterable<>(CONFIGS.getOrDefault(modId, Collections.emptyMap()).values());
	}

	public static @Nullable Config getConfig(String modId, String configId) {
		return CONFIGS.getOrDefault(modId, Collections.emptyMap()).get(configId);
	}

	private static @NotNull Iterator<Config> itr() {
		return new AllConfigsIterator();
	}

	private static class AllConfigsIterator implements Iterator<Config> {
		final Iterator<Map<String, Config>> itr1 = CONFIGS.values().iterator();
		Iterator<Config> itr2;

		private AllConfigsIterator() {
			if (this.itr1.hasNext()) {
				this.itr2 = this.itr1.next().values().iterator();
			}
		}

		@Override
		public boolean hasNext() {
			return this.itr1.hasNext() || this.itr2.hasNext();
		}

		@Override
		public Config next() {
			while (!this.itr2.hasNext() && this.itr1.hasNext()) {
				this.itr2 = this.itr1.next().values().iterator();
			}

			return this.itr2.next();
		}
	}
}
