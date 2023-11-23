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

package org.quiltmc.loader.api.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Additional metadata that should be implemented by plugin-provided mods that wish to rely on quilt's solver to
 * implement provides or dependency handling. */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface ModMetadataExt extends ModMetadata, ModMetadataToBeMovedToPlugins {

	// Dependency handling

	/** @return True if quilt-loader should use {@link #depends()} and {@link #breaks()} to generate {@link Rule}s for
	 *         the solver, or false if your plugin should handle them instead. */
	default boolean shouldQuiltDefineDependencies() {
		return false;
	}

	/** @return True if quilt-loader should use {@link #provides()} to generate {@link LoadOption}s for the solver, or
	 *         false if your plugin should handle them instead. */
	default boolean shouldQuiltDefineProvides() {
		return false;
	}

	default ModLoadType loadType() {
		return ModLoadType.ALWAYS;
	}

	@Nullable
	ModPlugin plugin();

	public enum ModLoadType {
		ALWAYS,
		IF_POSSIBLE,
		IF_REQUIRED;
	}

	public interface ModPlugin {
		String pluginClass();

		Collection<String> packages();
	}

	// Runtime

	Map<String, Collection<ModEntrypoint>> getEntrypoints();

	Map<String, String> languageAdapters();

	/** Entrypoint holder. Since plugins aren't expected to read from this only creation is supported. */
	@ApiStatus.NonExtendable
	public interface ModEntrypoint {

		/** @return A new {@link ModEntrypoint} using the default adapter and the given value. */
		public static ModEntrypoint create(String value) {
			return new AdapterLoadableClassEntry(value);
		}

		public static ModEntrypoint create(String adapter, String value) {
			return new AdapterLoadableClassEntry(adapter, value);
		}
	}
}
