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

package org.quiltmc.loader.api.plugin;

import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A reported error during plugin loading, which is shown in the error screen. This doesn't necessarily indicate an
 * error - however reporting any errors will cause the plugin loading to halt at the end of the current cycle. */
@ApiStatus.NonExtendable
public interface QuiltDisplayedError {

	/** Adds more lines which are shown in the log and the crash report file, NOT in the gui.
	 * 
	 * @return this. */
	QuiltDisplayedError appendReportText(String... lines);

	/** Adds more lines of description. */
	QuiltDisplayedError appendDescription(QuiltLoaderText... descriptions);

	QuiltDisplayedError setOrdering(int priority);

	/** Adds more lines of additional information, which is hidden from the user by default. */
	QuiltDisplayedError appendAdditionalInformation(QuiltLoaderText... information);

	/** Adds a {@link Throwable} to this error - which will be included in the crash-report file, but will not be shown
	 * in the gui. */
	QuiltDisplayedError appendThrowable(Throwable t);

	/** Defaults to {@link PluginGuiManager#iconLevelError()}. */
	QuiltDisplayedError setIcon(QuiltLoaderIcon icon);

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	default QuiltPluginButton addFileViewButton(Path openedPath) {
		return addFileViewButton(QuiltLoaderText.translate("button.view_file", openedPath.getFileName()), openedPath);
	}

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	QuiltPluginButton addFileViewButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	default QuiltPluginButton addFileEditButton(Path openedPath) {
		return addFileEditButton(QuiltLoaderText.translate("button.edit_file", openedPath.getFileName()), openedPath);
	}

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	QuiltPluginButton addFileEditButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file browser showing the selected folder. */
	QuiltPluginButton addFolderViewButton(QuiltLoaderText name, Path openedFolder);

	/** Adds a button to this error, which will open the specified URL in a browser window. */
	QuiltPluginButton addOpenLinkButton(QuiltLoaderText name, String url);

	/** Adds a button to this error, which opens the quilt user support forum. */
	QuiltPluginButton addOpenQuiltSupportButton();

	QuiltPluginButton addCopyTextToClipboardButton(QuiltLoaderText name, String fullText);

	QuiltPluginButton addCopyFileToClipboardButton(QuiltLoaderText name, Path openedFile);

	QuiltPluginButton addOnceActionButton(QuiltLoaderText name, QuiltLoaderText disabledText, Runnable action);

	QuiltPluginButton addActionButton(QuiltLoaderText name, Runnable action);

	/** Changes this error message to be "fixed". */
	void setFixed();

	@ApiStatus.NonExtendable
	public interface QuiltPluginButton {
		QuiltPluginButton text(QuiltLoaderText text);

		QuiltPluginButton icon(QuiltLoaderIcon icon);

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
