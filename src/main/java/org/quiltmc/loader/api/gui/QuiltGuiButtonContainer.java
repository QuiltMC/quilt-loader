package org.quiltmc.loader.api.gui;

import java.nio.file.Path;

import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;

public interface QuiltGuiButtonContainer {

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	default QuiltErrorButton addFileViewButton(Path openedPath) {
		return addFileViewButton(QuiltLoaderText.translate("button.view_file", openedPath.getFileName()), openedPath);
	}

	/** Adds a button to this error, which will open a file browser, selecting the given file. */
	QuiltErrorButton addFileViewButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	default QuiltErrorButton addFileEditButton(Path openedPath) {
		return addFileEditButton(QuiltLoaderText.translate("button.edit_file", openedPath.getFileName()), openedPath);
	}

	/** Adds a button to this error, which will open a file editor, editing the given file. */
	QuiltErrorButton addFileEditButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error which will open a file viewer for the given file. */
	default QuiltErrorButton addFileOpenButton(Path openedPath) {
		return addFileOpenButton(QuiltLoaderText.translate("button.open_file", openedPath.getFileName()), openedPath);
	}

	/** Adds a button to this error, which will open a file viewer, viewing the given file. */
	QuiltErrorButton addFileOpenButton(QuiltLoaderText name, Path openedPath);

	/** Adds a button to this error, which will open a file browser showing the selected folder. */
	QuiltErrorButton addFolderViewButton(QuiltLoaderText name, Path openedFolder);

	/** Adds a button to this error, which will open the specified URL in a browser window. */
	QuiltErrorButton addOpenLinkButton(QuiltLoaderText name, String url);

	/** Adds a button to this error, which opens the quilt user support forum. */
	QuiltErrorButton addOpenQuiltSupportButton();

	QuiltErrorButton addCopyTextToClipboardButton(QuiltLoaderText name, String fullText);

	QuiltErrorButton addCopyFileToClipboardButton(QuiltLoaderText name, Path openedFile);

	QuiltErrorButton addOnceActionButton(QuiltLoaderText name, QuiltLoaderText disabledText, Runnable action);

	QuiltErrorButton addActionButton(QuiltLoaderText name, Runnable action);
}
