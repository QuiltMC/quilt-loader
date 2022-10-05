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

package org.quiltmc.loader.api.plugin.gui;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

public interface PluginGuiManager {

	// Icons

	default PluginGuiIcon allocateIcon(BufferedImage image) {
		return allocateIcon(Collections.singletonMap(image.getWidth(), image));
	}

	PluginGuiIcon allocateIcon(Map<Integer, BufferedImage> image);

	// Builtin icons

	PluginGuiIcon iconFolder();

	PluginGuiIcon iconUnknownFile();

	PluginGuiIcon iconTextFile();

	PluginGuiIcon iconZipFile();

	PluginGuiIcon iconJarFile();

	PluginGuiIcon iconJsonFile();

	PluginGuiIcon iconJavaClassFile();

	PluginGuiIcon iconPackage();

	PluginGuiIcon iconJavaPackage();

	PluginGuiIcon iconDisabled();

	PluginGuiIcon iconQuilt();

	PluginGuiIcon iconTick();

	PluginGuiIcon iconCross();

	PluginGuiIcon iconLevelFatal();

	PluginGuiIcon iconLevelError();

	PluginGuiIcon iconLevelWarn();

	PluginGuiIcon iconLevelConcern();

	PluginGuiIcon iconLevelInfo();
}
