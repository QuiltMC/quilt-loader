/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.gui.QuiltBasicWindow;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.api.gui.QuiltGuiMessagesTab;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.report.QuiltReport;
import org.quiltmc.loader.impl.report.QuiltReport.CrashReportSaveFailed;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** The main entry point for all quilt-based stuff. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
@Deprecated
public final class QuiltGuiEntry {
	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayError(String mainText, Throwable exception, boolean warnEarly, boolean exitAfter) {
		if (warnEarly) {
			Log.error(LogCategory.GUI, "An error occurred: " + mainText, exception);
		}

		GameProvider provider = QuiltLoaderImpl.INSTANCE.tryGetGameProvider();

		if ((provider == null || provider.canOpenGui()) && !GraphicsEnvironment.isHeadless()) {

			QuiltReport report = new QuiltReport("Crashed!");
			// It's arguably the most important version - if anything goes wrong while writing this report
			// at least we know what code was used to generate it.
			report.overview("Quilt Loader Version: " + QuiltLoaderImpl.VERSION);
			report.addStacktraceSection("Crash", 0, exception);
			try {
				QuiltLoaderImpl.INSTANCE.appendModTable(report.addStringSection("Mods", 0)::lines);
			} catch (Throwable t) {
				report.addStacktraceSection("Exception while building the mods table", 0, t);
			}

			Path crashReportFile = null;
			String crashReportText = null;
			try {
				crashReportFile = report.writeInDirectory(QuiltLoader.getGameDir());
			} catch (CrashReportSaveFailed e) {
				crashReportText = e.fullReportText;
			}

			String title = "Quilt Loader " + QuiltLoaderImpl.VERSION;
			QuiltBasicWindow<Void> window = QuiltLoaderGui.createBasicWindow();
			window.title(QuiltLoaderText.of(title));
			window.mainText(QuiltLoaderText.of(mainText));

			QuiltGuiMessagesTab messages = window.addMessagesTab(QuiltLoaderText.EMPTY);
			window.restrictToSingleTab();

			QuiltJsonGuiMessage error = new QuiltJsonGuiMessage(null, "quilt_loader", QuiltLoaderText.translate("error.unhandled"));
			error.appendDescription(QuiltLoaderText.translate("error.unhandled_launch.desc"));
			error.setOrdering(-100);
			error.addOpenQuiltSupportButton();
			messages.addMessage(error);

			if (crashReportText != null) {
				error = new QuiltJsonGuiMessage(null, "quilt_loader", QuiltLoaderText.translate("error.failed_to_save_crash_report"));
				error.setIcon(GuiManagerImpl.ICON_LEVEL_ERROR);
				error.appendDescription(QuiltLoaderText.translate("error.failed_to_save_crash_report.desc"));
				error.appendAdditionalInformation(QuiltLoaderText.translate("error.failed_to_save_crash_report.info"));
				error.addCopyTextToClipboardButton(QuiltLoaderText.translate("button.copy_crash_report"), crashReportText);
				messages.addMessage(error);
			}

			if (crashReportFile != null) {
				window.addFileOpenButton(QuiltLoaderText.translate("button.open_crash_report"), crashReportFile);
				window.addCopyFileToClipboardButton(QuiltLoaderText.translate("button.copy_crash_report"), crashReportFile);
			}

			window.addFolderViewButton(QuiltLoaderText.translate("button.open_mods_folder"), QuiltLoaderImpl.INSTANCE.getModsDir());

			QuiltErrorButton continueBtn = window.addContinueButton();
			continueBtn.text(QuiltLoaderText.translate("button.exit"));
			continueBtn.icon(QuiltLoaderGui.iconLevelError());

			try {
				QuiltLoaderGui.open(window);
			} catch (LoaderGuiException e) {
				if (exitAfter) {
					Log.warn(LogCategory.GUI, "Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}
}
