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

import java.util.Optional;
import java.util.function.Supplier;

import org.quiltmc.loader.api.config.values.ValueTreeNode;

/**
 * A typed key to be used for setting and getting metadata of a {@link ValueTreeNode} or {@link Config}.
 *
 * <p>See also {@link TrackedValue.Builder#metadata}, {@link Config.Builder#metadata}, and {@link Config.SectionBuilder#metadata}
 */
public final class MetadataType<T, B extends MetadataType.Builder<T>> {
	private final Supplier<Optional<T>> defaultValueSupplier;
	private final Supplier<B> builderSupplier;

	private MetadataType(Supplier<Optional<T>> defaultValueSupplier, Supplier<B> builderSupplier) {
		this.defaultValueSupplier = defaultValueSupplier;
		this.builderSupplier = builderSupplier;
	}

	/**
	 * @return an optional containing the default value if this type has one, or an empty value if not
	 */
	public Optional<T> getDefaultValue() {
		return this.defaultValueSupplier.get();
	}

	public B newBuilder() {
		return this.builderSupplier.get();
	}

	public static <T, B extends MetadataType.Builder<T>> MetadataType<T, B> create(Supplier<Optional<T>> defaultValueSuplier, Supplier<B> builderSupplier) {
		return new MetadataType<>(defaultValueSuplier, builderSupplier);
	}

	public static <T, B extends MetadataType.Builder<T>> MetadataType<T, B> create(Supplier<B> builderSupplier) {
		return create(Optional::empty, builderSupplier);
	}

	public interface Builder<T> {
		T build();
	}
}
