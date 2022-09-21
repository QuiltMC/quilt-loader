package org.quiltmc.loader.api.plugin;

import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.Text;

/** A reported error during plugin loading, which is shown in the error screen. This doesn't necessarily indicate an
 * error - however reporting any errors will cause the plugin loading to halt at the end of the current cycle. */
@ApiStatus.NonExtendable
public interface QuiltPluginError {

	/** Adds more lines which are shown in the log and the crash report file, NOT in the gui.
	 * 
	 * @return this. */
	QuiltPluginError appendReportText(String... lines);

	/** Adds more lines of description. */
	QuiltPluginError appendDescription(Text... descriptions);

	/** Adds more lines of additional information, which is hidden from the user by default. */
	QuiltPluginError appendAdditionalInformation(Text... information);

	/** Adds a {@link Throwable} to this error - which will be included in the crash-report file, but will not be shown
	 * in the gui. */
	QuiltPluginError appendThrowable(Throwable t);

	/** Defaults to {@link PluginGuiManager#iconLevelError()}. */
	QuiltPluginError setIcon(PluginGuiIcon icon);

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	QuiltPluginError addFileViewButton(Text name, Path openedPath);

	/** Adds a button to this error, which will open the specified URL in a browser window. */
	QuiltPluginError addOpenLinkButton(Text name, String url);
}
