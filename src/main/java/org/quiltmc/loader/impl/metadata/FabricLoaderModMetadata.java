/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.metadata;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.FabricModMetadataWrapper;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;

/**
 * Internal variant of the ModMetadata interface.
 */
public interface FabricLoaderModMetadata extends ModMetadata, ConvertibleModMetadata {
	int getSchemaVersion();

	default String getOldStyleLanguageAdapter() {
		return "org.quiltmc.loader.impl.language.JavaLanguageAdapter";
	}

	Map<String, String> getLanguageAdapterDefinitions();
	Collection<NestedJarEntry> getJars();
	Collection<String> getMixinConfigs(EnvType type);
	@Nullable
	String getAccessWidener();
	boolean loadsInEnvironment(EnvType type);

	Collection<String> getOldInitializers();
	List<EntrypointMetadata> getEntrypoints(String type);
	Collection<String> getEntrypointKeys();

	void emitFormatWarnings();

	@Override
	default FabricLoaderModMetadata asFabricModMetadata() {
		return this;
	}

	@Override
	default InternalModMetadata asQuiltModMetadata() {
		return new FabricModMetadataWrapper(this);
	}

	void setVersion(Version version);
	void setDependencies(Collection<ModDependency> dependencies);
}
