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

import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class BuiltinPluginContext extends BasePluginContext {

	final QuiltLoaderPlugin plugin;

	public BuiltinPluginContext(QuiltPluginManagerImpl manager, String pluginId, QuiltLoaderPlugin plugin) {
		super(manager, pluginId);
		this.plugin = plugin;
	}

	@Override
	public Path pluginPath() {
		throw new UnsupportedOperationException("Builtin plugins don't support pluginPath()");
	}

	@Override
	public QuiltLoaderPlugin plugin() {
		return plugin;
	}
}
