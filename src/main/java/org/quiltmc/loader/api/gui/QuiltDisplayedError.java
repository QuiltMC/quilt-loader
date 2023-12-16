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

package org.quiltmc.loader.api.gui;

import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;

/** A reported error during plugin loading, which is shown in the error screen. This doesn't necessarily indicate an
 * error - however reporting any errors will cause the plugin loading to halt at the end of the current cycle. */
@ApiStatus.NonExtendable
public interface QuiltDisplayedError extends QuiltGuiButtonContainer {

	/** Adds more lines which are shown in the log and the crash report file, NOT in the gui.
	 * 
	 * @return this. */
	QuiltDisplayedError appendReportText(String... lines);

	/** Adds more lines of description. */
	QuiltDisplayedError appendDescription(QuiltLoaderText... descriptions);

	/** Removes all description text that was added by {@link #appendDescription(QuiltLoaderText...)}. Useful for
	 * changing the description after the window has already been shown. */
	QuiltDisplayedError clearDescription();

	QuiltDisplayedError setOrdering(int priority);

	/** Adds more lines of additional information, which is hidden from the user by default. */
	QuiltDisplayedError appendAdditionalInformation(QuiltLoaderText... information);

	/** Removes all additional text that was added by {@link #appendAdditionalInformation(QuiltLoaderText...)}. Useful
	 * for changing the description after the window has already been shown. */
	QuiltDisplayedError clearAdditionalInformation();

	/** Adds a {@link Throwable} to this error - which will be included in the crash-report file, but will not be shown
	 * in the gui. */
	QuiltDisplayedError appendThrowable(Throwable t);

	QuiltLoaderIcon icon();

	/** Defaults to {@link QuiltLoaderGui#iconLevelError()}. */
	QuiltDisplayedError setIcon(QuiltLoaderIcon icon);

	/** Obtains the current {@link QuiltTreeNode} for this error, or creates one if this doesn't have a tree node.
	 * 
	 * @return A tree node which is displayed after the text, but above any buttons. */
	QuiltTreeNode treeNode();

	/** Sets the {@link #treeNode()} to the given node. If you pass null the current tree node will be removed.
	 * 
	 * @return this. */
	QuiltDisplayedError treeNode(QuiltTreeNode node);

	// For backwards compatibility we need to keep these button methods around.

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	@Override
	default QuiltErrorButton addFileViewButton(Path openedPath) {
		return QuiltGuiButtonContainer.super.addFileViewButton(openedPath);
	}

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	@Override
	QuiltErrorButton addFileViewButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	@Override
	default QuiltErrorButton addFileEditButton(Path openedPath) {
		return QuiltGuiButtonContainer.super.addFileEditButton(openedPath);
	}

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	@Override
	QuiltErrorButton addFileEditButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file browser showing the selected folder. */
	@Override
	QuiltErrorButton addFolderViewButton(QuiltLoaderText name, Path openedFolder);

	/** Adds a button to this error, which will open the specified URL in a browser window. */
	@Override
	QuiltErrorButton addOpenLinkButton(QuiltLoaderText name, String url);

	/** Adds a button to this error, which opens the quilt user support forum. */
	@Override
	QuiltErrorButton addOpenQuiltSupportButton();

	@Override
	QuiltErrorButton addCopyTextToClipboardButton(QuiltLoaderText name, String fullText);

	@Override
	QuiltErrorButton addCopyFileToClipboardButton(QuiltLoaderText name, Path openedFile);

	@Override
	QuiltErrorButton addOnceActionButton(QuiltLoaderText name, QuiltLoaderText disabledText, Runnable action);

	@Override
	QuiltErrorButton addActionButton(QuiltLoaderText name, Runnable action);

	/** Changes this error message to be "fixed". If {@link #setIcon(QuiltLoaderIcon)} hasn't been called then the icon
	 * is set to {@link QuiltLoaderGui#iconTick()} */
	void setFixed();

	/** @return True if {@link #setFixed()} has been called. */
	boolean isFixed();

	/** Adds an action that will be ran when {@link #setFixed()} is called. */
	void addOnFixedListener(Runnable action);

	@ApiStatus.NonExtendable
	public interface QuiltErrorButton {
		QuiltErrorButton text(QuiltLoaderText text);

		QuiltLoaderIcon icon();

		QuiltErrorButton icon(QuiltLoaderIcon icon);

		/** Enables this button. This is the default state. */
		default void enable() {
			setEnabled(true, null);
		}

		/** Changes the "enabled" state of this button, which controls whether the action associated with this button
		 * can run.
		 * 
		 * @param enabled
		 * @param disabledMessage Shown when the user hovers over the button and it's disabled. This is ignored when
		 *            enabled is true. */
		void setEnabled(boolean enabled, QuiltLoaderText disabledMessage);
	}
}
