/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl;

import java.nio.file.Path;

import org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl;

public class QuiltPluginManagerForTests extends QuiltPluginManagerImpl {

	public QuiltPluginManagerForTests(Path gameDir, Path configDir, Path modsDir, Path cacheDir) {
		super(gameDir, configDir, modsDir, cacheDir, null, true, new QuiltLoaderConfig());
	}

	@Override
	protected boolean isTest() {
		return true;
	}
}
