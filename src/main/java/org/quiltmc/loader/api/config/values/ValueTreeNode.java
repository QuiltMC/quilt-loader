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

package org.quiltmc.loader.api.config.values;

import org.quiltmc.loader.api.config.MetadataType;

public interface ValueTreeNode {
	ValueKey getKey();

	Iterable<String> flags();

	boolean hasFlag(String flag);

	<M> Iterable<M> metadata(MetadataType<M> type);

	<M> boolean hasMetadata(MetadataType<M> type);

	interface Parent extends ValueTreeNode, Iterable<ValueTreeNode> {

	}
}
