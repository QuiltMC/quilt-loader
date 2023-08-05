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
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonAction;
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
	/** Opens the given {@link QuiltJsonGui} in a new swing window.
	 * 
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(QuiltJsonGui tree) throws Exception {
		open(tree, null, true);
	}

	/** Opens the given {@link QuiltJsonGui} in a new swing window.
	 * 
	 * @param forceFork If true then this will create a new process to host the window, false will always use this
	 *            process, and null will only fork if the current operating system doesn't support LWJGL + swing windows
	 *            at the same time (such as mac osx).
	 * @param shouldWait If true then this call will wait until either the user clicks the "continue" button or the
	 *            window is closed before returning, otherwise this method will return as soon as the window has opened.
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(QuiltJsonGui tree, Boolean forceFork, boolean shouldWait) throws Exception {
		QuiltFork.openErrorGui(tree, shouldWait);
	}

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
			QuiltJsonGui tree = new QuiltJsonGui(title, mainText);

			QuiltJsonGuiMessage error = new QuiltJsonGuiMessage(tree, "quilt_loader", QuiltLoaderText.translate("error.unhandled"));
			error.appendDescription(QuiltLoaderText.translate("error.unhandled_launch.desc"));
			error.setOrdering(-100);
			error.addOpenQuiltSupportButton();
			tree.messages.add(error);

			if (crashReportText != null) {
				error = new QuiltJsonGuiMessage(tree, "quilt_loader", QuiltLoaderText.translate("error.failed_to_save_crash_report"));
				error.setIcon(GuiManagerImpl.ICON_LEVEL_ERROR);
				error.appendDescription(QuiltLoaderText.translate("error.failed_to_save_crash_report.desc"));
				error.appendAdditionalInformation(QuiltLoaderText.translate("error.failed_to_save_crash_report.info"));
				error.addCopyTextToClipboardButton(QuiltLoaderText.translate("button.copy_crash_report"), crashReportText);
				tree.messages.add(error);
			}

			if (crashReportFile != null) {
				tree.addButton(QuiltLoaderText.translate("button.open_crash_report").toString(), "text_file", QuiltBasicButtonAction.OPEN_FILE)//
					.arg("file", crashReportFile.toString());
				tree.addButton(QuiltLoaderText.translate("button.copy_crash_report").toString(), QuiltBasicButtonAction.PASTE_CLIPBOARD_FILE)//
					.arg("file", crashReportFile.toString());
			}

			tree.addButton(QuiltLoaderText.translate("button.open_mods_folder").toString(), "folder", QuiltBasicButtonAction.VIEW_FOLDER)
				.arg("folder", QuiltLoaderImpl.INSTANCE.getModsDir().toString());

			tree.addButton(QuiltLoaderText.translate("button.exit").toString(), QuiltJsonGui.QuiltBasicButtonAction.CLOSE);

			try {
				QuiltFork.openErrorGui(tree, true);
			} catch (LoaderGuiClosed ignored) {
				// That's expected as we're crashing anyway
			} catch (Exception e) {
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
