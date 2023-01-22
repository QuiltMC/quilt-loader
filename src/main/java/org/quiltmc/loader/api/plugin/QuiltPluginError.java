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
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A reported error during plugin loading, which is shown in the error screen. This doesn't necessarily indicate an
 * error - however reporting any errors will cause the plugin loading to halt at the end of the current cycle. */
@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface QuiltPluginError {

	/** Adds more lines which are shown in the log and the crash report file, NOT in the gui.
	 * 
	 * @return this. */
	QuiltPluginError appendReportText(String... lines);

	/** Adds more lines of description. */
	QuiltPluginError appendDescription(QuiltLoaderText... descriptions);

	QuiltPluginError setOrdering(int priority);

	/** Adds more lines of additional information, which is hidden from the user by default. */
	QuiltPluginError appendAdditionalInformation(QuiltLoaderText... information);

	/** Adds a {@link Throwable} to this error - which will be included in the crash-report file, but will not be shown
	 * in the gui. */
	QuiltPluginError appendThrowable(Throwable t);

	/** Defaults to {@link PluginGuiManager#iconLevelError()}. */
	QuiltPluginError setIcon(PluginGuiIcon icon);

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	QuiltPluginButton addFileViewButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file browser showing the selected folder. */
	QuiltPluginButton addFolderViewButton(QuiltLoaderText name, Path openedFolder);

	/** Adds a button to this error, which will open the specified URL in a browser window. */
	QuiltPluginButton addOpenLinkButton(QuiltLoaderText name, String url);

	QuiltPluginButton addCopyTextToClipboardButton(QuiltLoaderText name, String fullText);

	QuiltPluginButton addCopyFileToClipboardButton(QuiltLoaderText name, Path openedFile);

	@ApiStatus.NonExtendable
	public interface QuiltPluginButton {
		QuiltPluginButton icon(PluginGuiIcon icon);
	}
}
