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

import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;

public abstract class BuiltinQuiltPlugin implements QuiltLoaderPlugin {
	private QuiltPluginContext context;

	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData) {
		this.context = context;
	}

	@Override
	public void unload(Map<String, LoaderValue> data) {
		throw new UnsupportedOperationException("Builtin plugins cannot be unloaded!");
	}

	public QuiltPluginContext context() {
		return context;
	}
}
