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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Map;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;

/** Internal mod metadata interface which stores implementation detail. */
public interface InternalModMetadata
	extends ModMetadata, ModMetadataExt, ConvertibleModMetadata {

	@Override
	default boolean shouldQuiltDefineDependencies() {
		return true;
	}

	@Override
	default boolean shouldQuiltDefineProvides() {
		return true;
	}

	Collection<String> jars();

	Collection<String> repositories();

	String intermediateMappings();

	@Override
	default FabricLoaderModMetadata asFabricModMetadata() {
		return new QuiltModMetadataWrapperFabric(this, null);
	}

	@Override
	default FabricLoaderModMetadata asFabricModMetadata(ModContainer quiltContainer) {
		return new QuiltModMetadataWrapperFabric(this, quiltContainer);
	}

	@Override
	default InternalModMetadata asQuiltModMetadata() {
		return this;
	}
}
