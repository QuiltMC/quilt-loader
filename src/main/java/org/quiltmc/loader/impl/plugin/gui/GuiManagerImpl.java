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

package org.quiltmc.loader.impl.plugin.gui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.impl.gui.QuiltJsonGui;

public class GuiManagerImpl implements PluginGuiManager {

	public static final PluginGuiIcon ICON_NULL = new PluginIconBuiltin("null");

	public static final PluginGuiIcon ICON_FOLDER = new PluginIconBuiltin("folder");
	public static final PluginGuiIcon ICON_TEXT_FILE = new PluginIconBuiltin("text_file");
	public static final PluginGuiIcon ICON_GENERIC_FILE = new PluginIconBuiltin("generic_file");
	public static final PluginGuiIcon ICON_JAR = new PluginIconBuiltin("jar");
	public static final PluginGuiIcon ICON_ZIP = new PluginIconBuiltin("zip");
	public static final PluginGuiIcon ICON_JSON = new PluginIconBuiltin("json");
	public static final PluginGuiIcon ICON_JAVA_CLASS = new PluginIconBuiltin("java_class");
	public static final PluginGuiIcon ICON_PACKAGE = new PluginIconBuiltin("package");
	public static final PluginGuiIcon ICON_JAVA_PACKAGE = new PluginIconBuiltin("java_package");
	public static final PluginGuiIcon ICON_DISABLED = new PluginIconBuiltin("disabled");
	public static final PluginGuiIcon ICON_QUILT = new PluginIconBuiltin("quilt");
	public static final PluginGuiIcon ICON_FABRIC = new PluginIconBuiltin("fabric");
	public static final PluginGuiIcon ICON_TICK = new PluginIconBuiltin("tick");
	public static final PluginGuiIcon ICON_CROSS = new PluginIconBuiltin("lesser_cross");
	public static final PluginGuiIcon ICON_LEVEL_FATAL = new PluginIconBuiltin("level_fatal");
	public static final PluginGuiIcon ICON_LEVEL_ERROR = new PluginIconBuiltin("level_error");
	public static final PluginGuiIcon ICON_LEVEL_WARN = new PluginIconBuiltin("level_warn");
	public static final PluginGuiIcon ICON_LEVEL_CONCERN = new PluginIconBuiltin("level_concern");
	public static final PluginGuiIcon ICON_LEVEL_INFO = new PluginIconBuiltin("level_info");

	private final List<Map<Integer, BufferedImage>> customIcons = new ArrayList<>();

	// Icons

	@Override
	public PluginGuiIcon allocateIcon(Map<Integer, BufferedImage> image) {
		int index = customIcons.size();
		customIcons.add(image);
		return new PluginIconCustom(index);
	}

	public void putIcons(QuiltJsonGui tree) {
		for (int i = 0; i < customIcons.size(); i++) {
			Map<Integer, BufferedImage> map = customIcons.get(i);
			int index = tree.allocateCustomIcon(map);
			if (index != i) {
				throw new IllegalStateException("GuiManagerImpl.putIcons must be called first!");
			}
		}
	}

	// Builtin

	@Override
	public PluginGuiIcon iconFolder() {
		return ICON_FOLDER;
	}

	@Override
	public PluginGuiIcon iconUnknownFile() {
		return ICON_GENERIC_FILE;
	}

	@Override
	public PluginGuiIcon iconTextFile() {
		return ICON_TEXT_FILE;
	}

	@Override
	public PluginGuiIcon iconZipFile() {
		return ICON_ZIP;
	}

	@Override
	public PluginGuiIcon iconJarFile() {
		return ICON_JAR;
	}

	@Override
	public PluginGuiIcon iconJsonFile() {
		return ICON_JSON;
	}

	@Override
	public PluginGuiIcon iconJavaClassFile() {
		return ICON_JAVA_CLASS;
	}

	@Override
	public PluginGuiIcon iconPackage() {
		return ICON_PACKAGE;
	}

	@Override
	public PluginGuiIcon iconJavaPackage() {
		return ICON_JAVA_PACKAGE;
	}

	@Override
	public PluginGuiIcon iconDisabled() {
		return ICON_DISABLED;
	}

	@Override
	public PluginGuiIcon iconQuilt() {
		return ICON_QUILT;
	}

	@Override
	public PluginGuiIcon iconTick() {
		return ICON_TICK;
	}

	@Override
	public PluginGuiIcon iconCross() {
		return ICON_CROSS;
	}

	@Override
	public PluginGuiIcon iconLevelFatal() {
		return ICON_LEVEL_FATAL;
	}

	@Override
	public PluginGuiIcon iconLevelError() {
		return ICON_LEVEL_ERROR;
	}

	@Override
	public PluginGuiIcon iconLevelWarn() {
		return ICON_LEVEL_WARN;
	}

	@Override
	public PluginGuiIcon iconLevelConcern() {
		return ICON_LEVEL_CONCERN;
	}

	@Override
	public PluginGuiIcon iconLevelInfo() {
		return ICON_LEVEL_INFO;
	}
}
