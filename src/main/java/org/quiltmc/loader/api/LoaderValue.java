/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.api;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value entry inside of mod metadata.
 */
@ApiStatus.NonExtendable
public interface LoaderValue {
	/**
	 * @return the type of the value
	 */
	LType type();

	String location();

	/**
	 * Coerces this custom value to an {@link LType#OBJECT}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not an object
	 */
	LObject asObject();

	/**
	 * Coerces this custom value to an {@link LType#ARRAY}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not an array
	 */
	LArray asArray();

	/**
	 * Coerces this custom value to a {@link LType#STRING}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a string
	 */
	String asString();

	/**
	 * Coerces this custom value to a {@link LType#NUMBER}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a number
	 */
	Number asNumber();

	/**
	 * Coerces this custom value to a {@link LType#BOOLEAN}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a boolean
	 */
	boolean asBoolean();

	/** @return True if everything other than {@link #location()} equals the given {@link LoaderValue}. */
	@Override
	boolean equals(Object obj);

	/** @return A hash code that's based on everything except {@link #location()}. */
	@Override
	int hashCode();

	/**
	 * Represents an {@link LType#OBJECT} value.
	 *
	 * <p>This implements {@link Map} and is immutable.
	 */
	@ApiStatus.NonExtendable
	interface LObject extends LoaderValue, Map<String, LoaderValue> {
		// TODO: Docs
		@Nullable
		@Override
		LoaderValue get(Object o);
	}

	/**
	 * Represents an {@link LType#ARRAY} value.
	 *
	 * <p>This implements {@link List} and is immutable.
	 */
	@ApiStatus.NonExtendable
	interface LArray extends LoaderValue, List<LoaderValue> {
		// TODO: Docs
		@Nullable
		@Override
		LoaderValue get(int i);
	}

	/**
	 * The possible types of a loader value.
	 */
	enum LType {
		/**
		 * Represents an object value.
		 * @see LObject
		 */
		OBJECT,
		/**
		 * Represents an array value.
		 * @see LArray
		 */
		ARRAY,
		/**
		 * Represents a {@link String} value.
		 * @see LoaderValue#asString()
		 */
		STRING,
		/**
		 * Represents a {@link Number} value.
		 * @see LoaderValue#asNumber()
		 */
		NUMBER,
		/**
		 * Represents a {@code boolean} value.
		 * @see LoaderValue#asBoolean()
		 */
		BOOLEAN,
		/**
		 * Represents a {@code null} value.
		 */
		NULL
	}
}
