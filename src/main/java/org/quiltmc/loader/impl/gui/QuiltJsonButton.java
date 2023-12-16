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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.impl.gui.QuiltJsonButton.QuiltBasicButtonAction;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class QuiltJsonButton extends QuiltGuiSyncBase implements QuiltErrorButton {

	interface QuiltButtonListener extends Listener {
		void onTextChanged();
		void onIconChanged();
		void onEnabledChanged();
	}

	public enum QuiltBasicButtonAction {
		CLOSE(QuiltLoaderGui.iconLevelError()),
		CONTINUE(QuiltLoaderGui.iconContinue()),
		VIEW_FILE(QuiltLoaderGui.iconUnknownFile(), "file"),
		EDIT_FILE(QuiltLoaderGui.iconUnknownFile(), "file"),
		VIEW_FOLDER(QuiltLoaderGui.iconFolder(), "folder"),
		OPEN_FILE(QuiltLoaderGui.iconUnknownFile(), "file"),

		/** Copies the given 'text' into the clipboard */
		PASTE_CLIPBOARD_TEXT(QuiltLoaderGui.iconClipboard(), "text"),

		/** Copies the contents of a {@link File} (given in 'file') into the clipboard. */
		PASTE_CLIPBOARD_FILE(QuiltLoaderGui.iconClipboard(), "file"),

		/** Copies a sub-sequence of characters from a {@link File} (given in 'file'), starting with the byte indexed by
		 * 'from' and stopping one byte before 'to' */
		PASTE_CLIPBOARD_FILE_SECTION(QuiltLoaderGui.iconClipboard(), "file", "from", "to"),
		OPEN_WEB_URL(QuiltLoaderGui.iconWeb(), "url"),

		/** Runs a {@link Runnable} in the original application, but only the first time the button is pressed. */
		RETURN_SIGNAL_ONCE(null),

		/** Runs a {@link Runnable} in the original application, every time the button is pressed. */
		RETURN_SIGNAL_MANY(null);

		public final QuiltLoaderIcon defaultIcon;
		private final Set<String> requiredArgs;

		private QuiltBasicButtonAction(QuiltLoaderIcon defaultIcon, String... args) {
			this.defaultIcon = defaultIcon;
			requiredArgs = new HashSet<>();
			Collections.addAll(requiredArgs, args);
		}
	}

	// Normal state
	public String text;

	@Nullable
	PluginIconImpl icon;

	public final QuiltJsonButton.QuiltBasicButtonAction action;
	public final Map<String, String> arguments = new HashMap<>();

	/** Only used for {@link QuiltBasicButtonAction#RETURN_SIGNAL_ONCE} and
	 * {@link QuiltBasicButtonAction#RETURN_SIGNAL_MANY} */
	Runnable returnSignalAction;
	String disabledText;
	boolean enabled;

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltJsonButton.QuiltBasicButtonAction action) {
		this(parent, text, icon, action, null);
	}

	public QuiltJsonButton(QuiltGuiSyncBase parent, String text, String icon, QuiltJsonButton.QuiltBasicButtonAction action, Runnable returnSignalAction) {
		super(parent);
		this.text = text;
		this.action = action;
		icon(action.defaultIcon);
		if (action == QuiltJsonButton.QuiltBasicButtonAction.RETURN_SIGNAL_ONCE || action == QuiltJsonButton.QuiltBasicButtonAction.RETURN_SIGNAL_MANY) {
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
		icon = obj.containsKey("icon") ? readChild(HELPER.expectValue(obj, "icon"), PluginIconImpl.class) : null;
		action = QuiltJsonButton.QuiltBasicButtonAction.valueOf(HELPER.expectString(obj, "action"));
		for (Map.Entry<String, LoaderValue> entry : HELPER.expectObject(obj, "arguments").entrySet()) {
			arguments.put(entry.getKey(), HELPER.expectString(entry.getValue()));
		}
	}

	public static QuiltJsonButton createUserSupportButton(QuiltGuiSyncBase parent) {
		QuiltLoaderText text = QuiltLoaderText.translate("button.quilt_forum.user_support");
		QuiltJsonButton button = new QuiltJsonButton(parent, text.toString(), null, QuiltJsonButton.QuiltBasicButtonAction.OPEN_WEB_URL);
		button.arg("url", "https://forum.quiltmc.org/c/support/9");
		return button;
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("text", lvf().string(text));
		if (icon != null) {
			map.put("icon", writeChild(icon));
		}
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
	public QuiltLoaderIcon icon() {
		return icon;
	}

	@Override
	public QuiltErrorButton icon(QuiltLoaderIcon newIcon) {
		if (newIcon == null) {
			if (action.defaultIcon == null) {
				this.icon = null;
			} else {
				return icon(action.defaultIcon);
			}
		} else {
			this.icon = PluginIconImpl.fromApi(newIcon);
		}
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("icon", icon == null ? lvf().nul() : writeChild(icon));
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
				invokeListeners(QuiltButtonListener.class, QuiltButtonListener::onTextChanged);
				break;
			}
			case "set_icon": {
				if (data.containsKey("icon")) {
					this.icon = readChild(HELPER.expectValue(data, "icon"), PluginIconImpl.class);
				} else {
					this.icon = null;
				}
				invokeListeners(QuiltButtonListener.class, QuiltButtonListener::onIconChanged);
				break;
			}
			case "enable": {
				this.enabled = true;
				invokeListeners(QuiltButtonListener.class, QuiltButtonListener::onEnabledChanged);
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
