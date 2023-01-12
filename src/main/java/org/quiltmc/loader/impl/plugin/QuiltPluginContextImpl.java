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

package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModPlugin;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltPluginContextImpl extends BasePluginContext {

	final ModLoadOption optionFrom;
	final Path pluginPath;
	final QuiltPluginClassLoader classLoader;
	final QuiltLoaderPlugin plugin;

	public QuiltPluginContextImpl(//
		QuiltPluginManagerImpl manager, ModLoadOption from, Map<String, LoaderValue> previousData //
	) throws ReflectiveOperationException {

		super(manager, from.id());
		this.optionFrom = from;
		this.pluginPath = from.resourceRoot();

		ClassLoader parent = getClass().getClassLoader();
		ModPlugin pluginMeta = from.metadata().plugin();
		if (pluginMeta == null) {
			throw new IllegalArgumentException("No plugin metadata!");
		}
		classLoader = new QuiltPluginClassLoader(manager, parent, pluginPath, pluginMeta);

		Class<?> cls = classLoader.loadClass(pluginMeta.pluginClass());
		Object obj = cls.getDeclaredConstructor().newInstance();
		this.plugin = (QuiltLoaderPlugin) obj;

		plugin.load(this, previousData);
	}

	@Override
	public QuiltLoaderPlugin plugin() {
		return plugin;
	}

	@Override
	public Path pluginPath() {
		return pluginPath;
	}

	Map<String, LoaderValue> unload() {
		Map<String, LoaderValue> data = new HashMap<>();

		plugin.unload(data);

		// Just to ensure the resulting map is not empty
		data.put("quilt.plugin.reloaded", LoaderValueFactory.getFactory().bool(true));

		return data;
	}
}
