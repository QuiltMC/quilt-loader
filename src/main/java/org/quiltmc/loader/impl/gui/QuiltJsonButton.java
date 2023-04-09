/*
 * Copyright 2023 QuiltMC
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonAction;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class QuiltJsonButton extends QuiltGuiSyncBase implements QuiltErrorButton {

	interface QuiltButtonListener {
		void onTextChanged();
		void onIconChanged();
		void onEnabledChanged();
	}

	// Normal state
	public String text;
	public String icon;
	public final QuiltBasicButtonAction action;
	public final Map<String, String> arguments = new HashMap<>();

	/** Only used for {@link QuiltBasicButtonAction#RETURN_SIGNAL_ONCE} and
	 * {@link QuiltBasicButtonAction#RETURN_SIGNAL_MANY} */
	Runnable returnSignalAction;
	String disabledText;
	boolean enabled;

	QuiltButtonListener guiListener;

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltBasicButtonAction action) {
		this(parent, text, icon, action, null);
	}

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltBasicButtonAction action, Runnable returnSignalAction) {
		super(parent);
		this.text = text;
		this.icon = icon != null ? icon : action.defaultIcon;
		this.action = action;
		if (action == QuiltBasicButtonAction.RETURN_SIGNAL_ONCE || action == QuiltBasicButtonAction.RETURN_SIGNAL_MANY) {
			if (returnSignalAction == null) {
				throw new NullPointerException("returnSignalAction");
			}
			this.returnSignalAction = returnSignalAction;
		} else if (returnSignalAction != null) {
			throw new IllegalArgumentException("Don't set a return signal action without using QuiltBasicButtonAction.RETURN_SIGNAL!");
		}
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

	public static QuiltJsonButton createUserSupportButton(QuiltGuiSyncBase parent) {
		QuiltLoaderText text = QuiltLoaderText.of("button.quilt_forum.user_support");
		QuiltJsonButton button = new QuiltJsonButton(parent, text.toString(), null, QuiltBasicButtonAction.OPEN_WEB_URL);
		button.arg("url", "https://forum.quiltmc.org/c/support/9");
		return button;
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
	public QuiltErrorButton text(QuiltLoaderText newText) {
		if (newText == null) {
			throw new NullPointerException("text");
		}
		this.text = newText.toString();
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("text", lvf().string(text));
			sendUpdate("set_text", lvf().object(map));
		}
		return this;
	}

	@Override
	public QuiltErrorButton icon(QuiltLoaderIcon newIcon) {
		if (newIcon == null) {
			this.icon = action.defaultIcon;
		} else {
			this.icon = PluginIconImpl.fromApi(newIcon).path;
		}
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("icon", lvf().string(icon));
			sendUpdate("set_icon", lvf().object(map));
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
			if (enabled) {
				sendSignal("enable");
			} else {
				Map<String, LoaderValue> map = new HashMap<>();
				map.put("disabled_text", lvf().string(disabledText));
				sendUpdate("disable", lvf().object(map));
			}
		}
	}

	public void sendClickToClient() {
		sendSignal("click");
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "click": {
				if (returnSignalAction != null) {
					returnSignalAction.run();
				} else {
					throw new IOException("Shouldn't receive button clicks for non-return-signal actions! " + action);
				}
				break;
			}
			case "set_text": {
				this.text = HELPER.expectString(data, "text");
				if (guiListener != null) {
					guiListener.onTextChanged();
				}
				break;
			}
			case "set_icon": {
				this.icon = HELPER.expectString(data, "icon");
				if (guiListener != null) {
					guiListener.onIconChanged();
				}
				break;
			}
			case "enable": {
				this.enabled = true;
				if (guiListener != null) {
					guiListener.onEnabledChanged();
				}
				break;
			}
			case "disable": {
				// TODO: Implement enabled/disabled and propogate this to the actual swing gui!
			}
			default: {
				super.handleUpdate(name, data);
			}
		}
	}
}
