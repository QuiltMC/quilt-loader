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

import java.util.HashMap;
import java.util.Map;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import org.quiltmc.loader.impl.config.Json5Serializer;
import org.quiltmc.loader.impl.config.NightConfigSerializer;
import org.quiltmc.loader.impl.util.SystemProperties;

public final class ConfigSerializers {
	private static final Map<String, Serializer> SERIALIZERS = new HashMap<>();

	static {
		SERIALIZERS.put("json5", Json5Serializer.INSTANCE);
		SERIALIZERS.put("toml", new NightConfigSerializer<>("toml", new TomlParser(), new TomlWriter()));
	}

	public static Serializer register(String fileType, Serializer serializer) {
		return SERIALIZERS.put(fileType, serializer);
	}

	public static Serializer getActualSerializer(String fileType) {
		if (SERIALIZERS.containsKey(fileType)) {
			return SERIALIZERS.get(fileType);
		} else {
			throw new RuntimeException("No serializer registered for extension '." + fileType + "'");
		}
	}

	public static Serializer getSerializer(String fileType) {
		return getActualSerializer(System.getProperty(SystemProperties.GLOBAL_CONFIG_EXTENSION, fileType));
	}
}
