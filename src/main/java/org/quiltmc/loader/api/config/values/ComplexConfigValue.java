package org.quiltmc.loader.api.config.values;

import org.quiltmc.loader.api.config.TrackedValue;

/**
 * Some config value that may need to be aware that it's being tracked
 */
public interface ComplexConfigValue {
	/**
	 * @param value the config value that is tracking this value
	 */
	void setValue(TrackedValue<?> value);

	/**
	 * @return a copy of this value if any of its fields are mutable, otherwise can return itself
	 */
	ComplexConfigValue copy();
}
