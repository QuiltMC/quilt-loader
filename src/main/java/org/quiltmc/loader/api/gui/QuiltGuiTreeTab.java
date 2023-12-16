package org.quiltmc.loader.api.gui;

public interface QuiltGuiTreeTab extends QuiltGuiTab {

	/** @return The root node. This node isn't displayed in the gui, but all of it's children are. */
	QuiltTreeNode rootNode();

	/** @return The current {@link QuiltWarningLevel} for this tab. The default is to inherit the level directly from
	 *         the {@link #rootNode()}s {@link QuiltTreeNode#maximumLevel()} */
	@Override
	QuiltWarningLevel level();

	/** Sets the level for this tab. This also sets {@link #inheritLevel(boolean)} to false.
	 * 
	 * @param level The new {@link QuiltWarningLevel}.
	 * @return this. */
	@Override
	QuiltGuiTab level(QuiltWarningLevel level);

	/** Defaults to true.
	 * 
	 * @param should If true then this {@link #level()} will be based off the {@link #rootNode()}
	 *            {@link QuiltTreeNode#maximumLevel()}.
	 * @return this */
	QuiltGuiTreeTab inheritLevel(boolean should);

	/** Controls whether nodes are visible in the GUI. Defaults to {@link QuiltWarningLevel#NONE}, which means that
	 * {@link QuiltWarningLevel#DEBUG_ONLY} nodes aren't shown. */
	QuiltGuiTreeTab visibilityLevel(QuiltWarningLevel level);
}
