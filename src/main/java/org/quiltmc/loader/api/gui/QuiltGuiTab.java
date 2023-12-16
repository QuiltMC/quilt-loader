package org.quiltmc.loader.api.gui;

public interface QuiltGuiTab {

	/** @return The current icon for this tab. */
	QuiltLoaderIcon icon();

	/** Sets the icon for this tab.
	 * 
	 * @param icon The new icon.
	 * @return this. */
	QuiltGuiTab icon(QuiltLoaderIcon icon);

	/** @return The current text for this tab. */
	QuiltLoaderText text();

	/** Sets the text for this tab.
	 * 
	 * @param text The new text.
	 * @return this. */
	QuiltGuiTab text(QuiltLoaderText text);

	/** @return The current {@link QuiltWarningLevel} for this tab. The default is {@link QuiltWarningLevel#NONE} */
	QuiltWarningLevel level();

	/** Sets the level for this tab.
	 * 
	 * @param level The new {@link QuiltWarningLevel}.
	 * @return this. */
	QuiltGuiTab level(QuiltWarningLevel level);
}
