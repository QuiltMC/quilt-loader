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

package org.quiltmc.loader.impl.config.util;

import org.quiltmc.loader.api.config.values.CompoundConfigValue;
import org.quiltmc.loader.api.config.values.ConfigSerializableObject;

public final class ConfigUtils {
	private static final Class<?>[] VALID_VALUE_CLASSES = new Class[] {
			Integer.TYPE,
			Integer.class,
			Long.TYPE,
			Long.class,
			Float.TYPE,
			Float.class,
			Double.TYPE,
			Double.class,
			Boolean.TYPE,
			Boolean.class,
			String.class
	};

	public static void assertValueType(Object object) {
		if (object == null) {
			throw new RuntimeException("Cannot create value with null default value");
		} else if (!isValidValue(object)) {
			throw new RuntimeException("Cannot create value of type '" + object.getClass() + "'");
		}
	}

	public static boolean isValidValue(Object object) {
		if (object == null) {
			return false;
		} else if (object instanceof ConfigSerializableObject) {
			return true;
		} else if (object instanceof CompoundConfigValue<?>) {
			while (object instanceof CompoundConfigValue<?>) {
				object = ((CompoundConfigValue<?>) object).getDefaultValue();
			}

			return isValidValue(object);
		} else {
			return isValidValueClass(object.getClass());
		}
	}

	public static boolean isValidValueClass(Class<?> valueClass) {
		if (valueClass.isEnum()) {
			return true;
		}

		for (Class<?> clazz : VALID_VALUE_CLASSES) {
			if (clazz == valueClass) {
				return true;
			}
		}

		return false;
	}
}
