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
import org.quiltmc.loader.api.config.exceptions.ConfigCreationException;
import org.quiltmc.loader.impl.util.ImmutableIterable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigsImpl {
	private static final Map<String, Map<String, Config>> CONFIGS = new TreeMap<>();

	private ConfigsImpl() {}

	public static void put(String familyId, Config config) {
		if (CONFIGS.containsKey(familyId) && CONFIGS.get(familyId).containsKey(config.id())) {
			throw new ConfigCreationException("Config '" + familyId + ':' + config.id() + "' already exists");
		}

		CONFIGS.computeIfAbsent(familyId, id -> new TreeMap<>()).put(config.id(), config);
	}

	public static Iterable<Config> getAll() {
		return ConfigsImpl::itr;
	}

	public static Iterable<Config> getConfigs(String familyId) {
		return new ImmutableIterable<>(CONFIGS.getOrDefault(familyId, Collections.emptyMap()).values());
	}

	public static @Nullable Config getConfig(String familyId, String configId) {
		return CONFIGS.getOrDefault(familyId, Collections.emptyMap()).get(configId);
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
			} else {
				this.itr2 = Collections.emptyIterator();
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
