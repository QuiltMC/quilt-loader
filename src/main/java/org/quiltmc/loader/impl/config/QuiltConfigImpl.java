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

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import org.quiltmc.config.api.ConfigEnvironment;
import org.quiltmc.config.api.Serializer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

public final class QuiltConfigImpl {
	private static ConfigEnvironment ENV;

	private QuiltConfigImpl() {
	}

	public static void init() {
		ENV = new ConfigEnvironment(QuiltLoaderImpl.INSTANCE.getConfigDir(), new NightConfigSerializer<>("toml", new TomlParser(), new TomlWriter()));

		ENV.registerSerializer(Json5Serializer.INSTANCE);

		for (Serializer serializer : QuiltLoaderImpl.INSTANCE.getEntrypoints("config_serializer", Serializer.class)) {
			Serializer oldValue = ENV.registerSerializer(serializer);

			if (oldValue != null) {
				Log.warn(LogCategory.CONFIG, "Replacing {} serializer {} with {}", serializer.getFileExtension(), oldValue.getClass(), serializer.getClass());
			}
		}
	}

	public static ConfigEnvironment getConfigEnvironment() {
		return ENV;
	}
}
