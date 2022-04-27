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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Consumer;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.config.values.ValueKey;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;
import org.quiltmc.loader.api.config.values.ValueTreeNode;
import org.quiltmc.loader.impl.config.util.ConfigUtils;
import org.quiltmc.loader.impl.config.builders.TrackedValueBuilderImpl;
import org.quiltmc.loader.impl.config.tree.TrackedValueImpl;
import org.quiltmc.loader.impl.config.values.ValueKeyImpl;

/**
 *
 */
@ApiStatus.NonExtendable
public interface TrackedValue<T> extends ValueTreeNode {
	ValueKey getKey();

	/**
	 * Returns the current value being tracked, or the override value if this value is being overridden
	 *
	 * @return some value
	 */
	T getValue();

	/**
	 * @return whether or not this value is being overridden
	 */
	boolean isBeingOverridden();

	/**
	 * @return the real value being tracked, even if it's being overridden
	 */
	T getRealValue();

	/**
	 * @param newValue the value to set
	 * @param serialize whether or not to serialize this values config file. Should be false only when deserializing
	 * @return the old value that's been replaced
	 */
	T setValue(T newValue, boolean serialize);

	/**
	 * Sets an override for this value to be returned by {@link #getValue}
	 *
	 * @param newValue some value
	 */
	void setOverride(T newValue);

	void removeOverride();

	T getDefaultValue();

	/**
	 * Adds a listener to this {@link TrackedValue} that's called whenever it's updated
	 *
	 * @param callback an update listener
	 */
	void register(UpdateCallback<T> callback);

	/**
	 * @return all flags associated with this {@link TrackedValue}
	 */
	Iterable<String> flags();

	boolean hasFlag(String flag);

	<M> Iterable<M> metadata(MetadataType<M> type);

	<M> boolean hasMetadata(MetadataType<M> type);

	Iterable<Constraint<T>> constraints();

	/**
	 * Checks the given value against all of this {@link TrackedValue}'s constraints
	 *
	 * @param value the value to check
	 * @return a list of error messages produced by any failing constraints, or an empty {@link Optional} if all passed
	 */
	Optional<Iterable<String>> checkForFailingConstraints(T value);

	/**
	 * Add a config value to be tracked.
	 *
	 * Config values can be one of the following types:
	 * <ul>
	 *     <li>Integer</li>
	 *     <li>Long</li>
	 *     <li>Float</li>
	 *     <li>Double</li>
	 *     <li>Boolean</li>
	 *     <li>String</li>
	 *     <li>A {@link ValueList} or {@link ValueMap} of any of these types</li>
	 * </ul>
	 *
	 * @param defaultValue the default value of the new {@link TrackedValue} to create
	 * @param key0 the first element of the key for the new {@link TrackedValue}
	 * @param keys any number of additional keys for the new {@link TrackedValue}
	 * @return a new {@link TrackedValue}
	 */
	static <T> TrackedValue<T> create(T defaultValue, String key0, String... keys) {
		ConfigUtils.assertValueType(defaultValue);

		return new TrackedValueImpl<>(new ValueKeyImpl(key0, keys), defaultValue, new LinkedHashSet<>(0), new LinkedHashMap<>(0), new ArrayList<>(0), new ArrayList<>(0));
	}

	/**
	 * Add a config value to be tracked.
	 *
	 * Config values can be one of the following types:
	 * <ul>
	 *     <li>Integer</li>
	 *     <li>Long</li>
	 *     <li>Float</li>
	 *     <li>Double</li>
	 *     <li>Boolean</li>
	 *     <li>String</li>
	 *     <li>A {@link ValueList} or {@link ValueMap} of any of these types</li>
	 * </ul>
	 *
	 * @param defaultValue the default value of the new {@link TrackedValue} to create
	 * @param key0 the first element of the key for the new {@link TrackedValue}
	 * @param creator a function that allows adding additional metadata to fields
	 * @return a new {@link TrackedValue}
	 */
	static <T> TrackedValue<T> create(T defaultValue, String key0, Consumer<Builder<T>> creator) {
		TrackedValueBuilderImpl<T> builder = new TrackedValueBuilderImpl<>(defaultValue, key0);

		creator.accept(builder);

		return builder.build();
	}

	interface UpdateCallback<T> {
		void onUpdate(ValueKey key, T oldValue, T newValue);
	}

	@ApiStatus.NonExtendable
	interface Builder<T> {
		/**
		 * Adds an additional key to this values key
		 *
		 * e.g. if this {@link TrackedValue}'s current key is "appearance.gui", calling this method with
		 * "inventory" would result in a key of "appearance.gui.inventory".
		 *
		 * @param key the key to append
		 * @return this
		 */
		Builder<T> key(String key);

		/**
		 * Adds a unique flag to this {@link TrackedValue}'s metadata
		 *
		 * @param flag a string value flag to track
		 * @return this
		 */
		Builder<T> flag(String flag);

		/**
		 * Adds a piece of metadata to this {@link TrackedValue}'s metadata
		 *
		 * A {@link TrackedValue} can have any number of values associated with each {@link MetadataType}.
		 *
		 * @param type the type of this metadata
		 * @param value a value to append to the resulting {@link TrackedValue}'s metadata
		 * @return this
		 */
		<M> Builder<T> metadata(MetadataType<M> type, M value);

		/**
		 * @param constraint a constraint that this value must satisfy
		 * @return this
		 */
		Builder<T> constraint(Constraint<T> constraint);

		/**
		 * Adds a default listener to the resulting {@link TrackedValue} that's called whenever it's updated
		 *
		 * @param callback an update listener
		 * @return this
		 */
		Builder<T> callback(UpdateCallback<T> callback);
	}
}
