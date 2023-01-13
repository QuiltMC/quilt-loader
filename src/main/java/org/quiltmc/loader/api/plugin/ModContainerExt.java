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

package org.quiltmc.loader.api.plugin;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface ModContainerExt extends ModContainer {
	@Override
	ModMetadataExt metadata();

	String pluginId();

	/** @return True if quilt-loader should add {@link #rootPath()} to it's classpath, false otherwise. */
	boolean shouldAddToQuiltClasspath();

	@Override
	default ClassLoader getClassLoader() {
		return QuiltLauncherBase.getLauncher().getClassLoader(this);
	}
}
