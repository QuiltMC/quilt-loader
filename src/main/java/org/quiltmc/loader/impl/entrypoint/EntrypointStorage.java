/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.impl.entrypoint;

import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.LanguageAdapterException;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.entrypoint.EntrypointException;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.util.*;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class EntrypointStorage {
	interface Entry {
		<T> T getOrCreate(Class<T> type) throws Exception;
		boolean isOptional();

		ModContainerExt getModContainer();
	}

	@SuppressWarnings("deprecation")
	private static class OldEntry implements Entry {
		private static final net.fabricmc.loader.language.LanguageAdapter.Options options = net.fabricmc.loader.language.LanguageAdapter.Options.Builder.create()
			.missingSuperclassBehaviour(net.fabricmc.loader.language.LanguageAdapter.MissingSuperclassBehavior.RETURN_NULL)
			.build();

		private final ModContainerExt mod;
		private final String languageAdapter;
		private final String value;
		private Object object;

		private OldEntry(ModContainerExt mod, String languageAdapter, String value) {
			this.mod = mod;
			this.languageAdapter = languageAdapter;
			this.value = value;
		}

		@Override
		public String toString() {
			return mod.metadata().id() + "->" + value;
		}

		@SuppressWarnings({ "unchecked" })
		@Override
		public <T> T getOrCreate(Class<T> type) throws Exception {
			if (object == null) {
				net.fabricmc.loader.language.LanguageAdapter adapter = (net.fabricmc.loader.language.LanguageAdapter) Class.forName(languageAdapter, true, QuiltLauncherBase.getLauncher().getTargetClassLoader()).getConstructor().newInstance();
				object = adapter.createInstance(value, options);
			}

			if (object == null || !type.isAssignableFrom(object.getClass())) {
				return null;
			} else {
				return (T) object;
			}
		}

		@Override
		public boolean isOptional() {
			return true;
		}

		@Override
		public ModContainerExt getModContainer() {
			return mod;
		}
	}

	private static final class NewEntry implements Entry {
		private final ModContainerExt mod;
		private final LanguageAdapter adapter;
		private final String value;
		private final Map<Class<?>, Object> instanceMap = new HashMap<>(1);

		NewEntry(ModContainerExt mod, LanguageAdapter adapter, String value) {
			this.mod = mod;
			this.adapter = adapter;
			this.value = value;
		}

		@Override
		public String toString() {
			return mod.metadata().id() + "->(0.3.x)" + value;
		}

		@SuppressWarnings("unchecked")
		@Override
		public synchronized <T> T getOrCreate(Class<T> type) throws Exception {
			// this impl allows reentrancy (unlike computeIfAbsent)
			T ret = (T) instanceMap.get(type);

			if (ret == null) {
				ret = adapter.create(mod, value, type);
				assert ret != null;
				T prev = (T) instanceMap.putIfAbsent(type, ret);
				if (prev != null) ret = prev;
			}

			return ret;
		}

		@Override
		public boolean isOptional() {
			return false;
		}

		@Override
		public ModContainerExt getModContainer() {
			return mod;
		}
	}

	private final Map<String, List<Entry>> entryMap = new HashMap<>();

	private List<Entry> getOrCreateEntries(String key) {
		return entryMap.computeIfAbsent(key, (z) -> new ArrayList<>());
	}

	public void addDeprecated(ModContainerExt modContainer, String adapter, String value) throws ClassNotFoundException, LanguageAdapterException {
		Log.debug(LogCategory.ENTRYPOINT, "Registering 0.3.x old-style initializer %s for mod %s", value, modContainer.metadata().id());
		OldEntry oe = new OldEntry(modContainer, adapter, value);
		getOrCreateEntries("main").add(oe);
		getOrCreateEntries("client").add(oe);
		getOrCreateEntries("server").add(oe);
	}

	public void add(ModContainerExt modContainer, String key, AdapterLoadableClassEntry metadata, Map<String, LanguageAdapter> adapterMap) throws Exception {
		if (!adapterMap.containsKey(metadata.getAdapter())) {
			throw new Exception("Could not find adapter '" + metadata.getAdapter() + "' (mod " + modContainer.metadata().id() + "!)");
		}

		Log.debug(LogCategory.ENTRYPOINT, "Registering new-style initializer %s for mod %s (key %s)", metadata.getValue(), modContainer.metadata().id(), key);
		getOrCreateEntries(key).add(new NewEntry(
				modContainer, adapterMap.get(metadata.getAdapter()), metadata.getValue()
				));
	}

	public boolean hasEntrypoints(String key) {
		return entryMap.containsKey(key);
	}

	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		List<Entry> entries = entryMap.get(key);
		if (entries == null) return Collections.emptyList();

		QuiltEntrypointException exception = null;
		List<T> results = new ArrayList<>(entries.size());

		for (Entry entry : entries) {
			try {
				T result = entry.getOrCreate(type);

				if (result != null) {
					results.add(result);
				}
			} catch (Throwable t) {
				if (exception == null) {
					exception = new QuiltEntrypointException(key, entry.getModContainer().metadata().id(), t);
				} else {
					exception.addSuppressed(t);
				}
			}
		}

		if (exception != null) {
			throw exception;
		}

		return results;
	}

	@SuppressWarnings("deprecation")
	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		List<Entry> entries = entryMap.get(key);
		if (entries == null) return Collections.emptyList();

		List<EntrypointContainer<T>> results = new ArrayList<>(entries.size());
		EntrypointException exc = null;

		for (Entry entry : entries) {
			EntrypointContainerImpl<T> container;

			if (entry.isOptional()) {
				try {
					T instance = entry.getOrCreate(type);
					if (instance == null) continue;

					container = new EntrypointContainerImpl<>(entry.getModContainer(), instance);
				} catch (Throwable t) {
					if (exc == null) {
						exc = new QuiltEntrypointException(key, entry.getModContainer().metadata().id(), t);
					} else {
						exc.addSuppressed(t);
					}

					continue;
				}
			} else {
				container = new EntrypointContainerImpl<>(entry.getModContainer(), () -> {
				try {
					return entry.getOrCreate(type);
				} catch (Exception ex) {
					throw new QuiltEntrypointException(key, entry.getModContainer().metadata().id(), ex);
				}
			});
			}

			results.add(container);
		}

		if (exc != null) throw exc;

		return results;
	}
}
