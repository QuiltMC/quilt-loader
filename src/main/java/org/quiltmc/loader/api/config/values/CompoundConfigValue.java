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

import org.jetbrains.annotations.ApiStatus;

/**
 * A collection of any number of basic or complex types that can be grown on demand.
 *
 * Will be either a {@link ValueList} or {@link ValueMap}
 *
 * Basic types: int, long, float, double, boolean, or String
 * Complex types: a {@link ValueList} or {@link ValueMap} of basic or complex types
 */
@ApiStatus.NonExtendable
public interface CompoundConfigValue<T> extends ComplexConfigValue {
	Class<T> getType();

	/**
	 * @return the default value for new elements of this collection
	 */
	T getDefaultValue();

	/**
	 * Appends the default value to this collection
	 */
	void grow();
}
