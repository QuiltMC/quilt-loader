/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.loader.impl.game;

import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import java.util.Collections;
import java.util.List;

/**
 * A MappingConfiguration for unobfuscated games. Has an empty MappingTreeView and only a single namespace.
 */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class EmptyMappingConfiguration implements MappingConfiguration {
	String namespace;

	public EmptyMappingConfiguration() {
		this("official"); // "official" is recommended for unobfuscated games
	}

	public EmptyMappingConfiguration(String namespace) {
		this.namespace = namespace;
	}
	@Override
	public MappingTreeView getMappings() {
		return null;
	}

	@Override
	public List<String> getNamespaces() {
		return Collections.emptyList();
	}

	@Override
	public String getTargetNamespace() {
		return namespace;
	}
}
