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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import java.util.List;
@ApiStatus.Experimental
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED) // only accessible to people implementing GameProviders
public interface MappingConfiguration {
	/**
	 * @return a view of all loaded mappings, or null if no mappings are loaded.
	 */
	@Nullable
	MappingTreeView getMappings();

	/**
	 * The supported namespaces of this MappingConfiguration. Empty if no mappings are loaded.
	 */
	List<String> getNamespaces();

	/**
	 * The runtime namespace to use. Must not be empty. Must be in {@link #getNamespaces()} unless the game is
	 * not obfuscated.
	 **/
	String getTargetNamespace();
}
