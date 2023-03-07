package org.quiltmc.loader.api.gui;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.QuiltDisplayedError;
import org.quiltmc.loader.impl.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.gui.QuiltFork;
import org.quiltmc.loader.impl.gui.QuiltJsonGuiMessage;

/** Central API for dealing with forked guis that should be shown before -or during- game loading, but not for the game
 * itself. */
public class QuiltLoaderGui {
	private QuiltLoaderGui() {}

	// Gui opening

	/** Creates a new error to be displayed in {@link #openErrorGui(QuiltDisplayedError)}. This doesn't do anything
	 * else.
	 * 
	 * @return A new {@link QuiltDisplayedError}. */
	public static QuiltDisplayedError createError(QuiltLoaderText title) {
		return new QuiltJsonGuiMessage(null, null, title);
	}

	/** @throws LoaderGuiException if something went wrong while opening the gui
	 * @throws LoaderGuiClosed if the gui was closed without fixing the errors. */
	public static void openErrorGui(QuiltDisplayedError error) throws LoaderGuiException, LoaderGuiClosed {
		openErrorGui(Collections.singletonList(error));
	}

	/** @throws LoaderGuiException if something went wrong while opening the gui
	 * @throws LoaderGuiClosed if the gui was closed without fixing the errors. */
	public static void openErrorGui(QuiltDisplayedError... errors) throws LoaderGuiException, LoaderGuiClosed {
		openErrorGui(Arrays.asList(errors));
	}

	/** @throws LoaderGuiException if something went wrong while opening the gui
	 * @throws LoaderGuiClosed if the gui was closed without fixing the errors. */
	public static void openErrorGui(List<QuiltDisplayedError> errors) throws LoaderGuiException, LoaderGuiClosed {
		QuiltFork.openErrorGui(errors);
	}

	// Icons

	public static QuiltLoaderIcon createIcon(BufferedImage image) {
		return createIcon(Collections.singletonMap(image.getWidth(), image));
	}

	public static QuiltLoaderIcon createIcon(Map<Integer, BufferedImage> images) {
		return GuiManagerImpl.allocateIcons(images);
	}

	public static QuiltLoaderIcon getModIcon(ModContainer mod) {
		return GuiManagerImpl.getModIcon(mod);
	}

	public static QuiltLoaderIcon getModIcon(String modid) {
		return getModIcon(QuiltLoader.getModContainer(modid).orElse(null));
	}

	// Builtin Icons

	public static QuiltLoaderIcon iconContinue() {
		return GuiManagerImpl.ICON_CONTINUE;
	}

	public static QuiltLoaderIcon iconContinueIgnoring() {
		return GuiManagerImpl.ICON_CONTINUE_BUT_IGNORE;
	}

	public static QuiltLoaderIcon iconFolder() {
		return GuiManagerImpl.ICON_FOLDER;
	}

	public QuiltLoaderIcon iconUnknownFile() {
		return GuiManagerImpl.ICON_GENERIC_FILE;
	}

	public static QuiltLoaderIcon iconTextFile() {
		return GuiManagerImpl.ICON_TEXT_FILE;
	}

	public static QuiltLoaderIcon iconZipFile() {
		return GuiManagerImpl.ICON_ZIP;
	}

	public static QuiltLoaderIcon iconJarFile() {
		return GuiManagerImpl.ICON_JAR;
	}

	public static QuiltLoaderIcon iconJsonFile() {
		return GuiManagerImpl.ICON_JSON;
	}

	public static QuiltLoaderIcon iconJavaClassFile() {
		return GuiManagerImpl.ICON_JAVA_CLASS;
	}

	public static QuiltLoaderIcon iconPackage() {
		return GuiManagerImpl.ICON_PACKAGE;
	}

	public static QuiltLoaderIcon iconJavaPackage() {
		return GuiManagerImpl.ICON_JAVA_PACKAGE;
	}

	public static QuiltLoaderIcon iconDisabled() {
		return GuiManagerImpl.ICON_DISABLED;
	}

	public static QuiltLoaderIcon iconQuilt() {
		return GuiManagerImpl.ICON_QUILT;
	}

	public static QuiltLoaderIcon iconFabric() {
		return GuiManagerImpl.ICON_FABRIC;
	}

	public static QuiltLoaderIcon iconTick() {
		return GuiManagerImpl.ICON_TICK;
	}

	public static QuiltLoaderIcon iconCross() {
		return GuiManagerImpl.ICON_CROSS;
	}

	public static QuiltLoaderIcon iconLevelFatal() {
		return GuiManagerImpl.ICON_LEVEL_FATAL;
	}

	public static QuiltLoaderIcon iconLevelError() {
		return GuiManagerImpl.ICON_LEVEL_ERROR;
	}

	public static QuiltLoaderIcon iconLevelWarn() {
		return GuiManagerImpl.ICON_LEVEL_WARN;
	}

	public static QuiltLoaderIcon iconLevelConcern() {
		return GuiManagerImpl.ICON_LEVEL_CONCERN;
	}

	public static QuiltLoaderIcon iconLevelInfo() {
		return GuiManagerImpl.ICON_LEVEL_INFO;
	}
}
