package org.quiltmc.loader.impl.gui;

import java.nio.file.Path;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.api.gui.QuiltGuiButtonContainer;
import org.quiltmc.loader.api.gui.QuiltLoaderText;

interface ButtonContainerImpl extends QuiltGuiButtonContainer {

	interface ButtonContainerListener {
		default void onButtonAdded(QuiltJsonButton button) {}
	}

	default QuiltJsonButton button(QuiltLoaderText name, QuiltJsonButton.QuiltBasicButtonAction action) {
		return button(name, action, null);
	}

	default QuiltJsonButton button(QuiltLoaderText name, QuiltJsonButton.QuiltBasicButtonAction action, Runnable run) {
		return addButton(new QuiltJsonButton(getThis(), name.toString(), null, action, run));
	}

	QuiltGuiSyncBase getThis();

	QuiltJsonButton addButton(QuiltJsonButton button);

	@Override
	default QuiltErrorButton addFileViewButton(QuiltLoaderText name, Path openedPath) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.VIEW_FILE).arg("file", openedPath.toString());
	}

	@Override
	default QuiltErrorButton addFileEditButton(QuiltLoaderText name, Path openedPath) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.EDIT_FILE).arg("file", openedPath.toString());
	}

	@Override
	default QuiltErrorButton addFileOpenButton(QuiltLoaderText name, Path openedPath) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.OPEN_FILE).arg("file", openedPath.toString());
	}

	@Override
	default QuiltErrorButton addFolderViewButton(QuiltLoaderText name, Path openedFolder) {
		if (FasterFiles.isRegularFile(openedFolder)) {
			return addFileViewButton(name, openedFolder);
		} else {
			return button(name, QuiltJsonButton.QuiltBasicButtonAction.VIEW_FOLDER)//
				.arg("folder", openedFolder.toString());
		}
	}

	@Override
	default QuiltErrorButton addOpenLinkButton(QuiltLoaderText name, String url) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.OPEN_WEB_URL).arg("url", url);
	}

	@Override
	default QuiltErrorButton addOpenQuiltSupportButton() {
		return addButton(QuiltJsonButton.createUserSupportButton(getThis()));
	}

	@Override
	default QuiltErrorButton addCopyTextToClipboardButton(QuiltLoaderText name, String fullText) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.PASTE_CLIPBOARD_TEXT).arg("text", fullText);
	}

	@Override
	default QuiltErrorButton addCopyFileToClipboardButton(QuiltLoaderText name, Path openedFile) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.PASTE_CLIPBOARD_FILE)//
			.arg("file", openedFile.toString());
	}

	@Override
	default QuiltErrorButton addOnceActionButton(QuiltLoaderText name, QuiltLoaderText disabledText, Runnable action) {
		QuiltJsonButton button = button(name, QuiltJsonButton.QuiltBasicButtonAction.RETURN_SIGNAL_ONCE, action);
		button.disabledText = disabledText.toString();
		return button;
	}

	@Override
	default QuiltErrorButton addActionButton(QuiltLoaderText name, Runnable action) {
		return button(name, QuiltJsonButton.QuiltBasicButtonAction.RETURN_SIGNAL_MANY, action);
	}
}
