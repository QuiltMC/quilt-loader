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

import org.quiltmc.loader.api.config.Serializer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.util.HashMap;
import java.util.Map;

public final class ConfigSerializers {
	private static final Map<String, Serializer> SERIALIZERS = new HashMap<>();

	static {
		// Needed for tests to pass
		initialize();
	}

	public static void initialize() {
		SERIALIZERS.put("json5", Json5Serializer.INSTANCE);

		for (Serializer serializer : QuiltLoaderImpl.INSTANCE.getEntrypoints("config_serializer", Serializer.class)) {
			Serializer oldValue = SERIALIZERS.put(serializer.getFileExtension(), serializer);

			if (oldValue != null) {
				Log.warn(LogCategory.CONFIG, "Replacing {} serializer {} with {}", serializer.getFileExtension(), oldValue.getClass(), serializer.getClass());
			}
		}
	}

	public static Serializer getActualSerializer(String fileType) {
		if (SERIALIZERS.containsKey(fileType)) {
			return SERIALIZERS.get(fileType);
		} else {
			throw new RuntimeException("No serializer registered for extension '." + fileType + "'");
		}
	}

	public static Serializer getSerializer(String fileType) {
		String globalFileType = System.getProperty(SystemProperties.GLOBAL_CONFIG_EXTENSION);

		if (globalFileType != null) {
			fileType = globalFileType;
		}

		if (SERIALIZERS.containsKey(fileType)) {
			return SERIALIZERS.get(fileType);
		} else {
			throw new RuntimeException("No serializer registered for extension '." + fileType + "'");
		}
	}
}
