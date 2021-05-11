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

	/**
	 * Coerces this custom value to an {@link LType#OBJECT}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not an object
	 */
	LObject getObject();

	/**
	 * Coerces this custom value to an {@link LType#ARRAY}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not an array
	 */
	LArray getArray();
	/**
	 * Coerces this custom value to a {@link LType#STRING}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a string
	 */
	String getString();

	/**
	 * Coerces this custom value to a {@link LType#NUMBER}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a number
	 */
	Number getNumber();

	/**
	 * Coerces this custom value to a {@link LType#BOOLEAN}.
	 *
	 * @return this value
	 * @throws ClassCastException if this value is not a boolean
	 */
	boolean getBoolean();

	/**
	 * Represents an {@link LType#OBJECT} value.
	 *
	 * <p>This implements {@link Map} and is immutable.
	 */
	@ApiStatus.NonExtendable
	interface LObject extends LoaderValue, Map<String, LoaderValue> {
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
		 * @see LoaderValue#getString()
		 */
		STRING,
		/**
		 * Represents a {@link Number} value.
		 * @see LoaderValue#getNumber()
		 */
		NUMBER,
		/**
		 * Represents a {@code boolean} value.
		 * @see LoaderValue#getBoolean()
		 */
		BOOLEAN,
		/**
		 * Represents a {@code null} value.
		 */
		NULL
	}
}
