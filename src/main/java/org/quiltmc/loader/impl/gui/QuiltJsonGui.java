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

import java.awt.datatransfer.Clipboard;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltJsonGui extends QuiltGuiSyncBase {
	public enum QuiltTreeWarningLevel {
		FATAL,
		ERROR,
		WARN,
		CONCERN,
		INFO,
		NONE;

		static final Map<String, QuiltTreeWarningLevel> nameToValue = new HashMap<>();

		public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

		static {
			for (QuiltTreeWarningLevel level : values()) {
				nameToValue.put(level.lowerCaseName, level);
			}
		}

		public boolean isHigherThan(QuiltTreeWarningLevel other) {
			return ordinal() < other.ordinal();
		}

		public boolean isAtLeast(QuiltTreeWarningLevel other) {
			return ordinal() <= other.ordinal();
		}

		public static QuiltTreeWarningLevel getHighest(QuiltTreeWarningLevel a, QuiltTreeWarningLevel b) {
			return a.isHigherThan(b) ? a : b;
		}

		/** @return The level to use, or null if the given char doesn't map to any level. */
		public static QuiltTreeWarningLevel fromChar(char c) {
			switch (c) {
				case '-':
					return NONE;
				case '+':
					return INFO;
				case '!':
					return WARN;
				case 'x':
					return ERROR;
				default:
					return null;
			}
		}

		static QuiltTreeWarningLevel read(String strValue) throws IOException {
			if (strValue.isEmpty()) {
				return NONE;
			}
			QuiltTreeWarningLevel level = nameToValue.get(strValue);
			if (level != null) {
				return level;
			} else {
				throw new IOException("Expected a valid QuiltTreeWarningLevel, but got '" + strValue + "'");
			}
		}

		public static QuiltTreeWarningLevel fromApiLevel(PluginGuiTreeNode.WarningLevel level) {
			switch (level) {
				case FATAL: {
					return FATAL;
				}
				case ERROR: {
					return ERROR;
				}
				case WARN: {
					return WARN;
				}
				case CONCERN: {
					return CONCERN;
				}
				case INFO: {
					return INFO;
				}
				case NONE:
				default:
					return NONE;
			}
		}
	}

	public enum QuiltBasicButtonAction {
		CLOSE(ICON_TYPE_DEFAULT),
		CONTINUE(ICON_TYPE_CONTINUE),
		VIEW_FILE(ICON_TYPE_GENERIC_FILE, "file"),
		EDIT_FILE(ICON_TYPE_GENERIC_FILE, "file"),
		VIEW_FOLDER(ICON_TYPE_FOLDER, "folder"),
		OPEN_FILE(ICON_TYPE_GENERIC_FILE, "file"),

		/** Copies the given 'text' into the clipboard */
		PASTE_CLIPBOARD_TEXT(ICON_TYPE_CLIPBOARD, "text"),

		/** Copies the contents of a {@link File} (given in 'file') into the clipboard. */
		PASTE_CLIPBOARD_FILE(ICON_TYPE_CLIPBOARD, "file"),

		/** Copies a sub-sequence of characters from a {@link File} (given in 'file'), starting with the byte indexed by
		 * 'from' and stopping one byte before 'to' */
		PASTE_CLIPBOARD_FILE_SECTION(ICON_TYPE_CLIPBOARD, "file", "from", "to"),
		OPEN_WEB_URL(ICON_TYPE_WEB, "url"),

		/** Runs a {@link Runnable} in the original application, but only the first time the button is pressed. */
		RETURN_SIGNAL_ONCE(ICON_TYPE_DEFAULT),

		/** Runs a {@link Runnable} in the original application, every time the button is pressed. */
		RETURN_SIGNAL_MANY(ICON_TYPE_DEFAULT);

		public final String defaultIcon;
		private final Set<String> requiredArgs;

		private QuiltBasicButtonAction(String defaultIcon, String... args) {
			this.defaultIcon = defaultIcon;
			requiredArgs = new HashSet<>();
			Collections.addAll(requiredArgs, args);
		}
	}

	/** No icon is displayed. */
	public static final String ICON_TYPE_DEFAULT = "";

	/** Move forward button */
	public static final String ICON_TYPE_CONTINUE = "continue";

	/** Generic folder. */
	public static final String ICON_TYPE_FOLDER = "folder";

	/** Generic (unknown contents) file. */
	public static final String ICON_TYPE_GENERIC_FILE = "generic_file";

	/** Generic non-Fabric jar file. */
	public static final String ICON_TYPE_JAR_FILE = "jar";

	/** Generic Fabric-related jar file. */
	public static final String ICON_TYPE_FABRIC_JAR_FILE = "jar+fabric";

	/** Generic Quilt-related jar file. */
	public static final String ICON_TYPE_QUILT_JAR_FILE = "jar+quilt";

	/** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_FABRIC = "fabric";

	/** Something related to Quilt (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_QUILT = "quilt";

	/** Generic JSON file. */
	public static final String ICON_TYPE_JSON = "json";

	/** A file called "fabric.mod.json". */
	public static final String ICON_TYPE_FABRIC_JSON = "json+fabric";

	/** A file called "quilt.mod.json". */
	public static final String ICON_TYPE_QUILT_JSON = "json+quilt";

	/** Java bytecode class file. */
	public static final String ICON_TYPE_JAVA_CLASS = "java_class";

	/** A folder inside of a Java JAR. */
	public static final String ICON_TYPE_PACKAGE = "package";

	/** A folder that contains Java class files. */
	public static final String ICON_TYPE_JAVA_PACKAGE = "java_package";

	/** A URL link */
	public static final String ICON_TYPE_WEB = "web_link";

	/** The {@link Clipboard} */
	public static final String ICON_TYPE_CLIPBOARD = "clipboard";

	/** A tick symbol, used to indicate that something matched. */
	public static final String ICON_TYPE_TICK = "tick";

	/** A cross symbol, used to indicate that something didn't match (although it's not an error). Used as the opposite
	 * of {@link #ICON_TYPE_TICK} */
	public static final String ICON_TYPE_LESSER_CROSS = "lesser_cross";

	public final CompletableFuture<Void> onClosedFuture = new CompletableFuture<>();

	public final String title;
	public final String mainText;

	public String messagesTabName = "_MESSAGES_";
	public final List<QuiltJsonGuiMessage> messages = new ArrayList<>();
	public final List<QuiltJsonGuiTreeTab> tabs = new ArrayList<>();
	public final List<QuiltJsonButton> buttons = new ArrayList<>();

	public QuiltJsonGui(String title, String mainText) {
		super(null);
		this.title = title;
		this.mainText = mainText == null ? "" : mainText;
	}

	public QuiltJsonGuiTreeTab addTab(String name) {
		QuiltJsonGuiTreeTab tab = new QuiltJsonGuiTreeTab(this, name);
		tabs.add(tab);
		return tab;
	}

	public QuiltJsonButton addButton(String text, QuiltBasicButtonAction action) {
		return addButton(text, action.defaultIcon, action);
	}

	public QuiltJsonButton addButton(String text, String icon, QuiltBasicButtonAction action) {
		QuiltJsonButton button = new QuiltJsonButton(this, text, icon, action);
		buttons.add(button);
		return button;
	}

	public QuiltJsonGui(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
		super(null, obj);
		if (parent != null) {
			throw new IOException("Root guis can't have parents!");
		}
		onClosedFuture.thenRun(this::onClosed);
		title = HELPER.expectString(obj, "title");
		mainText = HELPER.expectString(obj, "mainText");

		messagesTabName = HELPER.expectString(obj, "messagesTabName");
		for (LoaderValue sub : HELPER.expectArray(obj, "messages")) {
			messages.add(readChild(sub, QuiltJsonGuiMessage.class));
		}

		for (LoaderValue sub : HELPER.expectArray(obj, "tabs")) {
			tabs.add(readChild(sub, QuiltJsonGuiTreeTab.class));
		}

		for (LoaderValue sub : HELPER.expectArray(obj, "buttons")) {
			buttons.add(readChild(sub, QuiltJsonButton.class));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("title", lvf().string(title));
		map.put("mainText", lvf().string(mainText));
		map.put("messagesTabName", lvf().string(messagesTabName));
		map.put("messages", lvf().array(write(messages)));
		map.put("tabs", lvf().array(write(tabs)));
		map.put("buttons", lvf().array(write(buttons)));
	}

	@Override
	String syncType() {
		return "root_gui";
	}

	public void open() {
		sendSignal("open");
	}

	public void onClosed() {
		sendSignal("closed");
	}

	public void waitUntilClosed() throws LoaderGuiException, LoaderGuiClosed {
		try {
			onClosedFuture.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LoaderGuiException(e);
		} catch (ExecutionException e) {
			throw new LoaderGuiException(e.getCause());
		}

		for (QuiltJsonGuiMessage message : messages) {
			if (!message.fixed) {
				throw LoaderGuiClosed.INSTANCE;
			}
		}
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "open": {
				if (!QuiltForkComms.isServer()) {
					throw new IOException("Can only open on the server!");
				}

				try {
					QuiltMainWindow.open(this, false);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				return;
			}
			case "closed": {
				if (!QuiltForkComms.isClient()) {
					throw new IOException("Can only receive 'closed' on the client!");
				}
				onClosedFuture.complete(null);
			}
			default: {
				throw new IOException("Unknown update '" + name + "'");
			}
		}
	}

	public QuiltTreeWarningLevel getMaximumWarningLevel() {
		QuiltTreeWarningLevel max = QuiltTreeWarningLevel.NONE;
		for (QuiltJsonGuiTreeTab tab : this.tabs) {
			if (tab.node.getMaximumWarningLevel().isHigherThan(max)) {
				max = tab.node.getMaximumWarningLevel();
			}
		}

		return max;
	}

	static void expectName(JsonReader reader, String expected) throws IOException {
		String name = reader.nextName();
		if (!expected.equals(name)) {
			throw new IOException("Expected '" + expected + "', but read '" + name + "'");
		}
	}

	static String readStringOrNull(JsonReader reader) throws IOException {
		if (reader.peek() == JsonToken.STRING) {
			return reader.nextString();
		} else {
			reader.nextNull();
			return null;
		}
	}
}
