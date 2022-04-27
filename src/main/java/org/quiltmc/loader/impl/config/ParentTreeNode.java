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

package org.quiltmc.loader.impl.config;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.ValueKey;
import org.quiltmc.loader.api.config.ValueTreeNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ParentTreeNode extends AbstractMetadataContainer implements ValueTreeNode.Parent {
	private final Trie.Node node;

	protected ParentTreeNode(Trie.Node node, Map<MetadataType<?>, List<?>> metadata, Set<String> flags) {
		super(flags, metadata);
		this.node = node;
	}

	@Override
	public ValueKey getKey() {
		return this.node.getKey();
	}

	@NotNull
	@Override
	public Iterator<ValueTreeNode> iterator() {
		return new Iterator<ValueTreeNode>() {
			private final Iterator<Trie.Node> itr = ParentTreeNode.this.node.iterator();

			@Override
			public boolean hasNext() {
				return itr.hasNext();
			}

			@Override
			public ValueTreeNode next() {
				return itr.next().getValue();
			}
		};
	}
}
