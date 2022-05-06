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

package org.quiltmc.loader.api.config;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A typed key to be used for setting and getting object metadata.
 */
@ApiStatus.NonExtendable
public interface MetadataType<T, B extends MetadataType.Builder<T>> {
	Class<T> getMetadataClass();

	/**
	 * @return an optional containing the default value if this type has one, or an empty value if not
	 */
	Optional<T> getDefaultValue();

	B newBuilder();

	static <T, B extends MetadataType.Builder<T>> MetadataType<T, B> create(Class<T> typeClass, Supplier<Optional<T>> defaultValueSuplier, Supplier<B> builderSupplier) {
		return new MetadataType<T, B>() {
			@Override
			public Class<T> getMetadataClass() {
				return typeClass;
			}

			@Override
			public Optional<T> getDefaultValue() {
				return defaultValueSuplier.get();
			}

			@Override
			public B newBuilder() {
				return builderSupplier.get();
			}
		};
	}

	static <T, B extends MetadataType.Builder<T>> MetadataType<T, B> create(Class<T> typeClass, Supplier<B> builderSupplier) {
		return create(typeClass, Optional::empty, builderSupplier);
	}

	interface Builder<T> {
		T build();
	}
}
