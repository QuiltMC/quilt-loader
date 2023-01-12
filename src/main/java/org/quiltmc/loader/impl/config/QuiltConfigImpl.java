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

import java.util.LinkedHashMap;
import java.util.Map;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import org.quiltmc.config.api.ConfigEnvironment;
import org.quiltmc.config.api.Serializer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltConfigImpl {
	private static ConfigEnvironment ENV;

	private QuiltConfigImpl() {
	}

	public static void init() {
		Map<String, Serializer> serializerMap = new LinkedHashMap<>();

		serializerMap.put("toml", new NightConfigSerializer<>("toml", new TomlParser(), new TomlWriter()));
		serializerMap.put("json5", Json5Serializer.INSTANCE);

		for (Serializer serializer : QuiltLoaderImpl.INSTANCE.getEntrypoints("config_serializer", Serializer.class)) {
			Serializer oldValue = serializerMap.put(serializer.getFileExtension(), serializer);

			if (oldValue != null) {
				Log.warn(LogCategory.CONFIG, "Replacing {} serializer {} with {}", serializer.getFileExtension(), oldValue.getClass(), serializer.getClass());
			}
		}

		String globalConfigExtension = System.getProperty(SystemProperties.GLOBAL_CONFIG_EXTENSION);
		String defaultConfigExtension = System.getProperty(SystemProperties.DEFAULT_CONFIG_EXTENSION);

		Serializer[] serializers = serializerMap.values().toArray(new Serializer[0]);

		if (globalConfigExtension != null && !serializerMap.containsKey(globalConfigExtension)) {
			throw new RuntimeException("Cannot use file extension " + globalConfigExtension + " globally: no matching serializer found");
		}

		if (defaultConfigExtension != null && !serializerMap.containsKey(defaultConfigExtension)) {
			throw new RuntimeException("Cannot use file extension " + defaultConfigExtension + " by default: no matching serializer found");
		}

		if (defaultConfigExtension == null) {
			ENV = new ConfigEnvironment(QuiltLoaderImpl.INSTANCE.getConfigDir(), globalConfigExtension, serializers[0]);

			for (int i = 1; i < serializers.length; ++i) {
				ENV.registerSerializer(serializers[i]);
			}
		} else {
			ENV = new ConfigEnvironment(QuiltLoaderImpl.INSTANCE.getConfigDir(), globalConfigExtension, serializerMap.get(defaultConfigExtension));

			for (Serializer serializer : serializers) {
				ENV.registerSerializer(serializer);
			}
		}
	}

	public static ConfigEnvironment getConfigEnvironment() {
		return ENV;
	}
}
