package org.quiltmc.loader.api.gui;

public interface QuiltGuiMessagesTab extends QuiltGuiTab {
	void addMessage(QuiltDisplayedError message);

	void removeMessage(QuiltDisplayedError message);
}
