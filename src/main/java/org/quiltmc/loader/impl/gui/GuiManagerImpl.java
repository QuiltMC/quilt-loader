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

package org.quiltmc.loader.impl.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class GuiManagerImpl {

	public static final PluginIconImpl ICON_NULL = new PluginIconImpl("null");

	public static final PluginIconImpl ICON_CONTINUE = new PluginIconImpl("continue");
	public static final PluginIconImpl ICON_CONTINUE_BUT_IGNORE = new PluginIconImpl("continue_but_ignore");
	public static final PluginIconImpl ICON_RELOAD = new PluginIconImpl("reload");
	public static final PluginIconImpl ICON_FOLDER = new PluginIconImpl("folder");
	public static final PluginIconImpl ICON_TEXT_FILE = new PluginIconImpl("text_file");
	public static final PluginIconImpl ICON_GENERIC_FILE = new PluginIconImpl("generic_file");
	public static final PluginIconImpl ICON_JAR = new PluginIconImpl("jar");
	public static final PluginIconImpl ICON_ZIP = new PluginIconImpl("zip");
	public static final PluginIconImpl ICON_JSON = new PluginIconImpl("json");
	public static final PluginIconImpl ICON_JAVA_CLASS = new PluginIconImpl("java_class");
	public static final PluginIconImpl ICON_PACKAGE = new PluginIconImpl("package");
	public static final PluginIconImpl ICON_JAVA_PACKAGE = new PluginIconImpl("java_package");
	public static final PluginIconImpl ICON_DISABLED = new PluginIconImpl("disabled");
	public static final PluginIconImpl ICON_QUILT = new PluginIconImpl("quilt");
	public static final PluginIconImpl ICON_FABRIC = new PluginIconImpl("fabric");
	public static final PluginIconImpl ICON_WEB_LINK = new PluginIconImpl("web_link");
	public static final PluginIconImpl ICON_CLIPBOARD = new PluginIconImpl("clipboard");
	public static final PluginIconImpl ICON_TICK = new PluginIconImpl("tick");
	public static final PluginIconImpl ICON_CROSS = new PluginIconImpl("lesser_cross");
	public static final PluginIconImpl ICON_TREE_DOT = new PluginIconImpl("missing");
	public static final PluginIconImpl ICON_LEVEL_FATAL = new PluginIconImpl("level_fatal");
	public static final PluginIconImpl ICON_LEVEL_ERROR = new PluginIconImpl("level_error");
	public static final PluginIconImpl ICON_LEVEL_WARN = new PluginIconImpl("level_warn");
	public static final PluginIconImpl ICON_LEVEL_CONCERN = new PluginIconImpl("level_concern");
	public static final PluginIconImpl ICON_LEVEL_INFO = new PluginIconImpl("level_info");

	private static final AtomicInteger NEXT_ICON_KEY = new AtomicInteger();
	private static final Map<Integer, Map<Integer, BufferedImage>> ICON_MAP = new ConcurrentHashMap<>();
	private static final Map<String, QuiltLoaderIcon> MOD_ICON_CACHE = new ConcurrentHashMap<>();

	// Icons

	public static QuiltLoaderIcon allocateIcons(Map<Integer, BufferedImage> imageSizeMap) {
		int index = NEXT_ICON_KEY.incrementAndGet();
		ICON_MAP.put(index, imageSizeMap);
		QuiltFork.uploadIcon(index, imageSizeMap);
		return new PluginIconImpl(index);
	}

	public static QuiltLoaderIcon allocateIcons(byte[][] imageBytes) {
		return new PluginIconImpl(imageBytes);
	}

	public static QuiltLoaderIcon getModIcon(ModContainer mod) {
		if (mod == null) {
			return ICON_GENERIC_FILE;
		}
		String modid = mod == null ? "" : mod.metadata().id();
		return MOD_ICON_CACHE.computeIfAbsent(modid, id -> {
			return computeModIcon(mod);
		});
	}

	private static QuiltLoaderIcon computeModIcon(ModContainer mod) {
		Map<String, byte[]> images = new HashMap<>();
		for (int size : new int[] { 16, 32 }) {
			String iconPath = mod.metadata().icon(size);
			if (iconPath != null && ! images.containsKey(iconPath)) {
				Path path = mod.getPath(iconPath);
				try (InputStream stream = Files.newInputStream(path)) {
					images.put(iconPath, FileUtil.readAllBytes(stream));
				} catch (IOException io) {
					// TODO: Warn about this somewhere!
					io.printStackTrace();
				}
			}
		}
		if (images.isEmpty()) {
			return ICON_GENERIC_FILE;
		}
		return allocateIcons(images.values().toArray(new byte[0][]));
	}
}
