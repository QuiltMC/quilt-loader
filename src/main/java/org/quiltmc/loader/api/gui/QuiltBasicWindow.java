package org.quiltmc.loader.api.gui;

import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;

/** A simple standard window which contains multiple tabs, each either:
 * <ul>
 * <li>A list of {@link QuiltDisplayedError} messages.</li>
 * <li>A tree of {@link QuiltTreeNode}.</li>
 * </ul>
 * This also has an optional text at the top of the window ({@link #mainText()}) and a set of buttons on the bottom of
 * the window.
 * 
 * @param <R> The return type for this window. */
public interface QuiltBasicWindow<R> extends QuiltLoaderWindow<R>, QuiltGuiButtonContainer {

	QuiltLoaderText mainText();

	void mainText(QuiltLoaderText text);

	/** If called then this window will only contain a single tab, and won't show the tab bar at the top of the screen.
	 * This must be called before opening this window. This must be called after the first tab has been added.
	 * 
	 * @throws IllegalStateException if called after this window has been opened, or 0 or more than 1 tab has been
	 *             added. */
	void restrictToSingleTab();

	QuiltGuiMessagesTab addMessagesTab(QuiltLoaderText name);

	QuiltGuiTreeTab addTreeTab(QuiltLoaderText name);

	QuiltGuiTreeTab addTreeTab(QuiltLoaderText name, QuiltTreeNode rootNode);

	/** @return A new {@link QuiltErrorButton} that will close this window and return the current {@link #returnValue()}
	 *         from the caller when pressed. */
	QuiltErrorButton addContinueButton();
}
