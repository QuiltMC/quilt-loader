package org.quiltmc.loader.impl.plugin.gui;

import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;

public class GuiManagerImpl implements PluginGuiManager {

	public static final GuiManagerImpl INSTANCE = new GuiManagerImpl();

	public static final PluginGuiIcon ICON_NULL = new PluginIconBuiltin("null");

	public static final PluginGuiIcon ICON_FOLDER = new PluginIconBuiltin("folder");
	public static final PluginGuiIcon ICON_FILE = new PluginIconBuiltin("file");
	public static final PluginGuiIcon ICON_JAR = new PluginIconBuiltin("jar");
	public static final PluginGuiIcon ICON_ZIP = new PluginIconBuiltin("zip");
	public static final PluginGuiIcon ICON_JSON = new PluginIconBuiltin("json");
	public static final PluginGuiIcon ICON_JAVA_CLASS = new PluginIconBuiltin("java_class");
	public static final PluginGuiIcon ICON_PACKAGE = new PluginIconBuiltin("package");
	public static final PluginGuiIcon ICON_JAVA_PACKAGE = new PluginIconBuiltin("java_package");
	public static final PluginGuiIcon ICON_DISABLED = new PluginIconBuiltin("disabled");
	public static final PluginGuiIcon ICON_QUILT = new PluginIconBuiltin("quilt");
	public static final PluginGuiIcon ICON_TICK = new PluginIconBuiltin("tick");
	public static final PluginGuiIcon ICON_CROSS = new PluginIconBuiltin("lesser_cross");

	// Icons

	@Override
	public PluginGuiIcon iconFolder() {
		return ICON_FOLDER;
	}

	@Override
	public PluginGuiIcon iconUnknownFile() {
		return ICON_FILE;
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
}
