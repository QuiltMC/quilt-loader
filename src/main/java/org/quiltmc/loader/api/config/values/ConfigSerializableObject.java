package org.quiltmc.loader.api.config.values;

import org.quiltmc.loader.api.config.TrackedValue;

/**
 * An object that can be represented as a basic or complex type for serialization purposes
 */
public interface ConfigSerializableObject<T> extends ComplexConfigValue {
	/**
	 * See {@link ComplexConfigValue}
	 *
	 * @param value the config value that is tracking this value
	 */
	default void setValue(TrackedValue<?> value) { }

	/**
	 * @return a new instance of this class created from the given representation
	 */
	ConfigSerializableObject<T> convertFrom(T representation);

	/**
	 * @return some representation value for serialization
	 */
	T getRepresentation();
}
