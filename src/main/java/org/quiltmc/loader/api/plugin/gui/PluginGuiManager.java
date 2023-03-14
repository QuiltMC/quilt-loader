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

package org.quiltmc.loader.api.plugin.gui;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/**
 * @deprecated Replaced with {@link QuiltLoaderGui}, kept only until we clear out all uses of this from quilt's codebase.
 */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
@Deprecated
public interface PluginGuiManager {

	// Icons

	default QuiltLoaderIcon allocateIcon(BufferedImage image) {
		return allocateIcon(Collections.singletonMap(image.getWidth(), image));
	}

	QuiltLoaderIcon allocateIcon(Map<Integer, BufferedImage> image);

	// Builtin icons

	QuiltLoaderIcon iconFolder();

	QuiltLoaderIcon iconUnknownFile();

	QuiltLoaderIcon iconTextFile();

	QuiltLoaderIcon iconZipFile();

	QuiltLoaderIcon iconJarFile();

	QuiltLoaderIcon iconJsonFile();

	QuiltLoaderIcon iconJavaClassFile();

	QuiltLoaderIcon iconPackage();

	QuiltLoaderIcon iconJavaPackage();

	QuiltLoaderIcon iconDisabled();

	QuiltLoaderIcon iconQuilt();

	QuiltLoaderIcon iconFabric();

	QuiltLoaderIcon iconTick();

	QuiltLoaderIcon iconCross();

	QuiltLoaderIcon iconLevelFatal();

	QuiltLoaderIcon iconLevelError();

	QuiltLoaderIcon iconLevelWarn();

	QuiltLoaderIcon iconLevelConcern();

	QuiltLoaderIcon iconLevelInfo();
}
