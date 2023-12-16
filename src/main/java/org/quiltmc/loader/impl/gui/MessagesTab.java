package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltGuiMessagesTab;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;

class MessagesTab extends AbstractTab implements QuiltGuiMessagesTab {

	interface MessageTabListener extends TabChangeListener {
		default void onMessageAdded(QuiltJsonGuiMessage message) {}
		default void onMessageRemoved(int index, QuiltJsonGuiMessage message) {}
	}

	final List<QuiltJsonGuiMessage> messages = new ArrayList<>();

	public MessagesTab(BasicWindow<?> parent, QuiltLoaderText text) {
		super(parent, text);
	}

	public MessagesTab(QuiltGuiSyncBase parent, LObject obj) throws IOException {
		super(parent, obj);
		for (LoaderValue value : HELPER.expectArray(obj, "messages")) {
			messages.add(readChild(value, QuiltJsonGuiMessage.class));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		super.write0(map);
		map.put("messages", lvf().array(write(messages)));
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "add_message": {
				QuiltJsonGuiMessage message = readChild(HELPER.expectValue(data, "message"), QuiltJsonGuiMessage.class);
				messages.add(message);
				invokeListeners(MessageTabListener.class, l -> l.onMessageAdded(message));
				break;
			}
			case "remove_message": {
				int index = HELPER.expectNumber(data, "index").intValue();
				QuiltJsonGuiMessage removed = messages.remove(index);
				if (removed != null) {
					invokeListeners(MessageTabListener.class, l -> l.onMessageRemoved(index, removed));
				}
				break;
			}
			default: {
				super.handleUpdate(name, data);
			}
		}
	}

	@Override
	String syncType() {
		return "tab_messages";
	}

	@Override
	public void addMessage(QuiltDisplayedError message) {
		QuiltJsonGuiMessage impl = (QuiltJsonGuiMessage) message;
		messages.add(impl);
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("message", writeChild(impl));
			sendUpdate("add_message", lvf().object(map));
		}
	}

	@Override
	public void removeMessage(QuiltDisplayedError message) {
		int index = messages.indexOf(message);
		if (index >= 0) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("index", lvf().number(index));
			sendUpdate("remove_message", lvf().object(map));
		}
	}

	@Override
	QuiltWarningLevel getInheritedLevel() {
		for (QuiltJsonGuiMessage msg : messages) {
			// TODO: Add levels to messages!
		}
		return QuiltWarningLevel.NONE;
	}
}
