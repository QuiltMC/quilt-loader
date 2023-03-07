package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.QuiltDisplayedError.QuiltPluginButton;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonAction;

public final class QuiltJsonButton extends QuiltGuiSyncBase implements QuiltPluginButton {

	// Normal state
	public final String text;
	public String icon;
	public final QuiltBasicButtonAction action;
	public final Map<String, String> arguments = new HashMap<>();

	/** Only used for {@link QuiltBasicButtonAction#RETURN_SIGNAL_ONCE} and
	 * {@link QuiltBasicButtonAction#RETURN_SIGNAL_MANY} */
	Runnable returnSignalAction;
	String disabledText;
	boolean enabled;

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltBasicButtonAction action) {
		this(parent, text, icon, action, null);
	}

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltBasicButtonAction action, Runnable returnSignalAction) {
		super(parent);
		this.text = text;
		this.icon = icon;
		this.action = action;
		this.returnSignalAction = returnSignalAction;
	}

	QuiltJsonButton(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
		super(parent, obj);
		text = HELPER.expectString(obj, "text");
		icon = HELPER.expectString(obj, "icon");
		action = QuiltBasicButtonAction.valueOf(HELPER.expectString(obj, "action"));
		for (Map.Entry<String, LoaderValue> entry : HELPER.expectObject(obj, "arguments").entrySet()) {
			arguments.put(entry.getKey(), HELPER.expectString(entry.getValue()));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("text", lvf().string(text));
		map.put("icon", lvf().string(icon));
		map.put("action", lvf().string(action.name()));
		Map<String, LoaderValue> argsMap = new HashMap<>();
		for (Entry<String, String> entry : arguments.entrySet()) {
			argsMap.put(entry.getKey(), lvf().string(entry.getValue()));
		}
		map.put("arguments", lvf().object(argsMap));
	}

	@Override
	String syncType() {
		return "button";
	}

	public QuiltJsonButton arg(String key, String value) {
		arguments.put(key, value);
		return this;
	}

	public QuiltJsonButton arguments(Map<String, String> args) {
		arguments.putAll(args);
		return this;
	}

	public QuiltJsonButton setAction(Runnable action) {
		this.returnSignalAction = action;
		return this;
	}

	@Override
	public QuiltPluginButton icon(QuiltLoaderIcon newIcon) {
		if (newIcon == null) {
			this.icon = action.defaultIcon;
		} else {
			this.icon = PluginIconImpl.fromApi(newIcon).path;
		}
		if (shouldSendUpdates()) {
			// TODO: Send the new icon!
		}
		return this;
	}

	@Override
	public void setEnabled(boolean enabled, QuiltLoaderText disabledMessage) {
		this.enabled = enabled;
		if (!enabled) {
			if (disabledMessage == null) {
				throw new NullPointerException("disabledMessage");
			} else {
				this.disabledText = disabledMessage.toString();
			}
		}

		if (shouldSendUpdates()) {
			// TODO: Send the new state!
		}
	}

	public void sendClickToClient() {
		sendSignal("click");
	}
}
