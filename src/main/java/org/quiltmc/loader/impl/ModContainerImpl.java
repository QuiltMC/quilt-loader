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

package org.quiltmc.loader.impl;

import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.metadata.AbstractModMetadata;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.QuiltModMetadataWrapperFabric;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class ModContainerImpl implements org.quiltmc.loader.api.ModContainer {
	private final InternalModMetadata meta;
	private final FabricLoaderModMetadata fabricMeta;
	private final List<List<Path>> sourcePaths;
	private final BasicSourceType sourceType;
	private final Path root;

	public ModContainerImpl(ModCandidate candidate) {
		this.meta = candidate.getMetadata();
		this.fabricMeta = meta.asFabricModMetadata();
		this.root = candidate.getInnerPath();

		// Yes, this is wrong.
		// Ideally we'd iterate back across the whole path until we reached
		this.sourcePaths = Collections.singletonList(Collections.singletonList(candidate.getOriginPath()));

		if (fabricMeta instanceof QuiltModMetadataWrapperFabric) {
			// A quilt mod
			// builtin mods currently can't be anything other than fabric meta mods
			sourceType = BasicSourceType.NORMAL_QUILT;
		} else if (AbstractModMetadata.TYPE_BUILTIN.equals(fabricMeta.getType())) {
			sourceType = BasicSourceType.BUILTIN;
		} else {
			sourceType = BasicSourceType.NORMAL_FABRIC;
		}
	}

	@Override
	public ModMetadata metadata() {
		return meta;
	}

	@Override
	public Path rootPath() {
		return root;
	}

	@Override
	public List<List<Path>> getSourcePaths() {
		return sourcePaths;
	}

	@Override
	public BasicSourceType getSourceType() {
		return sourceType;
	}

	@Deprecated
	public FabricLoaderModMetadata getInfo() {
		return fabricMeta;
	}

	public InternalModMetadata getInternalMeta() {
		return meta;
	}

	@Override
	public String toString() {
		return String.format("%s %s", fabricMeta.getId(), fabricMeta.getVersion());
	}
}