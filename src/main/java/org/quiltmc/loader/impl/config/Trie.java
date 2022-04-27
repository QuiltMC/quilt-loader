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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.ValueKey;
import org.quiltmc.loader.api.config.ValueTreeNode;

public final class Trie {
	private final Node root = new Node(null, null, null);

	private int modCount;

	public Iterable<ValueTreeNode> leaves() {
		return LeafItr::new;
	}

	public Iterable<ValueTreeNode> nodes() {
		return new Iterable<ValueTreeNode>() {
			@NotNull
			@Override
			public Iterator<ValueTreeNode> iterator() {
				return new Iterator<ValueTreeNode>() {
					private final Iterator<Node> itr = Trie.this.root.iterator();

					@Override
					public boolean hasNext() {
						return this.itr.hasNext();
					}

					@Override
					public ValueTreeNode next() {
						return this.itr.next().getValue();
					}
				};
			}
		};
	}

	public ValueTreeNode get(String key0, String... keys) {
		return this.root.getOrCreateNChild(key0, keys).value;
	}

	public ValueTreeNode put(ValueTreeNode value, String key0, String... keys) {
		int modifiedCount = this.modCount;

		Node node = this.root.getOrCreateNChild(key0, keys);
		ValueTreeNode oldValue = node.getValue();
		node.setValue(value);

		// Only increment the number of modifications if a new node wasn't created by the call to getOrCreateNChild
		if (modifiedCount == this.modCount) {
			++this.modCount;
		}

		return oldValue;
	}

	public ValueTreeNode put(ValueKey key, ValueTreeNode value) {
		int modifiedCount = this.modCount;

		Node node = this.root;

		for (String keyComponent : key) {
			if (node.value == null) {
				node.setValue(new ParentTreeNode(node, Collections.emptyMap(), Collections.emptySet()));
			}

			node = node.getOrCreateChild(keyComponent);
		}

		node.setValue(value);

		// Only increment the number of modifications if a new node wasn't created by the call to getOrCreateNChild
		if (modifiedCount == this.modCount) {
			++this.modCount;
		}

		return null;
	}

	public void put(ValueKey key, SectionBuilderImpl sectionBuilder) {
		Node node = this.root;

		for (String keyComponent : key) {
			node = node.getOrCreateChild(keyComponent);
		}

		node.setValue(new ParentTreeNode(node, sectionBuilder.metadata, sectionBuilder.flags));
	}

	public TrackedValue<?> get(Iterable<String> key) {
		Node node = this.root;

		for (String k : key) {
			node = node.getOrCreateChild(k);
		}

		return (TrackedValue<?>) node.value;
	}

	private class LeafItr implements Iterator<ValueTreeNode> {
		private final int modCount = Trie.this.modCount;
		private final Deque<Iterator<Node>> iterators = new ArrayDeque<>();

		private LeafItr() {
			this.iterators.addFirst(Trie.this.root.children.values().iterator());
		}

		@Override
		public boolean hasNext() {
			while (!this.iterators.isEmpty() && !this.iterators.peek().hasNext()) {
				this.iterators.pop();
			}

			return !this.iterators.isEmpty();
		}

		@Override
		public ValueTreeNode next() {
			this.checkForComodification();

			Iterator<Node> itr = this.iterators.getFirst();

			Node n = itr.next();

			if (itr.hasNext()) {
				this.iterators.addFirst(itr);
			}

			while (n.hasChildren()) {
				itr = n.children.values().iterator();
				n = itr.next();

				this.iterators.addFirst(itr);
			}

			return n.getValue();
		}

		private void checkForComodification() {
			if (this.modCount != Trie.this.modCount) {
				throw new ConcurrentModificationException();
			}
		}
	}

	public class Node implements Iterable<Node> {
		private final Node parent;
		private final ValueKey key;
		private final Map<String, Node> children = new LinkedHashMap<>();
		private ValueTreeNode value;

		private Node(Node parent, ValueKey key, ValueTreeNode value) {
			this.parent = parent;
			this.key = key;
			this.value = value;
		}

		private Node(Node parent, ValueKey key) {
			this(parent, key, null);
		}

		public boolean hasChildren() {
			return !this.children.isEmpty();
		}

		public ValueKey getKey() {
			return this.key;
		}

		public Node getParent() {
			return this.parent;
		}

		private Node getOrCreateNChild(String key0, String... keys) {
			if (keys.length == 0) {
				return this.getOrCreateChild(key0);
			} else {
				int modCount = Trie.this.modCount;

				Node n = this.getOrCreateChild(key0);

				for (int i = 0, keysLength = keys.length; i < keysLength; i++) {
					String key = keys[i];

					if (i < keys.length - 1) {
						n.setValue(new ParentTreeNode(n, Collections.emptyMap(), Collections.emptySet()));
					}

					n = n.getOrCreateChild(key);
				}

				// Collapse all new node creations into a single modification
				if (Trie.this.modCount > modCount) {
					Trie.this.modCount = modCount + 1;
				}

				return n;
			}
		}

		private Node getOrCreateChild(String key) {
			if (this.children.containsKey(key)) {
				return this.children.get(key);
			} else {
				++Trie.this.modCount;

				Node child = new Node(this, this.key == null
						? new ValueKeyImpl(key)
						: this.key.child(key)
				);

				this.children.put(key, child);

				return child;
			}
		}

		public ValueTreeNode getValue() {
			return this.value;
		}

		@NotNull
		@Override
		public Iterator<Node> iterator() {
			return this.children.values().iterator();
		}

		public void setValue(ValueTreeNode value) {
			if (this.value != null) {
				throw new UnsupportedOperationException("Cannot put node '" + value.getKey() + "': Node already exists");
			}

			this.value = value;
		}
	}
}
