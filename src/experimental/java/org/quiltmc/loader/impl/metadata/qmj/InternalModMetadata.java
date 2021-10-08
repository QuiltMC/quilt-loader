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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Map;

import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

/**
 * Internal mod metadata interface which stores implementation detail.
 */
public interface InternalModMetadata extends ModMetadata, ModMetadataToBeMovedToPlugins, ConvertibleModMetadata {
	Collection<ModProvided> provides();

	ModLoadType loadType();

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints();

	Collection<AdapterLoadableClassEntry> getPlugins();

	Collection<String> jars();

	Map<String, String> languageAdapters();

	Collection<String> repositories();

	@Override
	default LoaderModMetadata asFabricModMetadata() {
	    return new QuiltModMetadataWrapper(this);
	}

	@Override
	default InternalModMetadata asQuiltModMetadata() {
	    return this;
	}
}
