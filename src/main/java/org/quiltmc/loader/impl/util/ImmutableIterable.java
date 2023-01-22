/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class ImmutableIterable<T> implements Iterable<T> {
	private final Iterable<T> itr;

	public ImmutableIterable(Iterable<T> itr) {
		this.itr = itr;
	}

	@NotNull
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			private final Iterator<T> itr = ImmutableIterable.this.itr.iterator();

			@Override
			public boolean hasNext() {
				return this.itr.hasNext();
			}

			@Override
			public T next() {
				return this.itr.next();
			}
		};
	}
}
