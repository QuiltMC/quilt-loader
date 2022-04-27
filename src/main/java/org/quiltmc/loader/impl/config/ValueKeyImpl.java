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

import java.util.Arrays;
import java.util.Iterator;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.config.ValueKey;

public final class ValueKeyImpl implements ValueKey {
	private final String string;
	private final String[] keys;

	public ValueKeyImpl(String[] keys) {
		if (keys.length == 0) {
			throw new IllegalArgumentException("Keys cannot be empty");
		}

		this.keys = new String[keys.length];

		System.arraycopy(keys, 0, this.keys, 0, keys.length);

		StringBuilder builder = new StringBuilder(this.keys[0]);

		for (int i = 0; i < this.keys.length; ++i) {
			if (this.keys[i] == null) {
				throw new IllegalArgumentException("No component of a key can be null");
			} else if (i > 0) {
				builder.append('.').append(this.keys[i]);
			}
		}

		this.string = builder.toString();
	}

	public ValueKeyImpl(String key0, String... keys) {
		this.keys = new String[keys.length + 1];
		this.keys[0] = key0;

		System.arraycopy(keys, 0, this.keys, 1, keys.length);

		StringBuilder builder = new StringBuilder(key0);

		for (int i = 0; i < this.keys.length; ++i) {
			if (this.keys[i] == null) {
				throw new IllegalArgumentException("No component of a key can be null");
			} else if (i > 0) {
				builder.append('.').append(this.keys[i]);
			}
		}

		this.string = builder.toString();
	}

	@Override
	public ValueKey child(String key) {
		String[] newKey = new String[this.keys.length + 1];

		System.arraycopy(this.keys, 0, newKey, 0, this.keys.length);

		newKey[newKey.length - 1] = key;

		return new ValueKeyImpl(newKey);
	}

	@Override
	public String toString() {
		return this.string;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ValueKeyImpl)) return false;

		ValueKeyImpl other = (ValueKeyImpl) obj;

		if (other.length() != this.length()) return false;

		for (int i = 0; i < this.length(); ++i) {
			if (!this.getKeyComponent(i).equals(other.getKeyComponent(i))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.keys);
	}

	@Override
	public int length() {
		return this.keys.length;
	}

	@Override
	public String getKeyComponent(int i) {
		return this.keys[i];
	}

	@NotNull
	@Override
	public Iterator<String> iterator() {
		return new Itr();
	}

	@Override
	public boolean startsWith(ValueKey key) {
		for (int i = 0; i < key.length() && i < this.length(); ++i) {
			if (!key.getKeyComponent(i).equals(this.keys[i])) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean isSibling(ValueKey key) {
		if (this.keys.length > 1 && this.keys.length == key.length()) {
			for (int i = 0; i < this.keys.length - 1; ++i) {
				if (!this.keys[i].equals(key.getKeyComponent(i))) {
					return false;
				}
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public String getLastComponent() {
		return this.keys[this.keys.length - 1];
	}

	@Override
	public ValueKey child(ValueKey key) {
		String[] newKey = new String[this.keys.length + key.length()];

		System.arraycopy(this.keys, 0, newKey, 0, this.keys.length);
		System.arraycopy(((ValueKeyImpl) key).keys, 0, newKey, this.keys.length, key.length());

		return new ValueKeyImpl(newKey);
	}

	private final class Itr implements Iterator<String> {
		private int i = 0;

		@Override
		public boolean hasNext() {
			return this.i < ValueKeyImpl.this.keys.length;
		}

		@Override
		public String next() {
			return ValueKeyImpl.this.keys[i++];
		}
	}
}
