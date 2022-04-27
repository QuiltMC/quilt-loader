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

import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.impl.util.ImmutableIterable;

import java.util.*;

public abstract class AbstractMetadataContainer {
	protected final Set<String> flags;
	protected final Map<MetadataType<?>, List<?>> metadata;

	protected AbstractMetadataContainer(Set<String> flags, Map<MetadataType<?>, List<?>> metadata) {
		this.flags = flags;
		this.metadata = metadata;
	}

	public Iterable<String> flags() {
		return new ImmutableIterable<>(this.flags);
	}

	@SuppressWarnings("unchecked")
	public <M> Iterable<M> metadata(MetadataType<M> type) {
		return new ImmutableIterable<>((Iterable<M>) this.metadata.getOrDefault(type, Collections.EMPTY_LIST));
	}

	public boolean hasFlag(String flag) {
		return this.flags.contains(flag);
	}

	public <M> boolean hasMetadata(MetadataType<M> type) {
		return this.metadata.containsKey(type) && !this.metadata.get(type).isEmpty();
	}
}
