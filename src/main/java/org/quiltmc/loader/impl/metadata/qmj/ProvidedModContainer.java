/*
 * Copyright 2023 QuiltMC
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

import java.nio.file.Path;
import java.util.List;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ProvidedModContainer implements ModContainerExt {

	final ProvidedModMetadata metadata;
	final ModContainerExt container;

	public ProvidedModContainer(ProvidedModMetadata metadata, ModContainerExt container) {
		this.metadata = metadata;
		this.container = container;
	}

	@Override
	public Path rootPath() {
		return container.rootPath();
	}

	@Override
	public List<List<Path>> getSourcePaths() {
		return container.getSourcePaths();
	}

	@Override
	public BasicSourceType getSourceType() {
		return container.getSourceType();
	}

	@Override
	public ModMetadataExt metadata() {
		return metadata;
	}

	@Override
	public String pluginId() {
		return container.pluginId();
	}

	@Override
	public String modType() {
		return "Provided";
	}

	@Override
	public boolean shouldAddToQuiltClasspath() {
		return false;
	}
}
