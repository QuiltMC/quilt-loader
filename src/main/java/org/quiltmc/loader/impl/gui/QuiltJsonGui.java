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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.QuiltDisplayedError.QuiltPluginButton;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.impl.FormattedException;
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

	public static final class QuiltJsonButton extends QuiltGuiSyncBase implements QuiltPluginButton {

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

	public static final class QuiltJsonGuiTreeTab extends QuiltGuiSyncBase {
		public final QuiltStatusNode node;

		/** The minimum warning level to display for this tab. */
		public QuiltTreeWarningLevel filterLevel = QuiltTreeWarningLevel.NONE;

		public QuiltJsonGuiTreeTab(QuiltGuiSyncBase parent, String name) {
			super(parent);
			this.node = new QuiltStatusNode(null, name);
		}

		public QuiltStatusNode addChild(String name) {
			return node.addChild(name);
		}

		QuiltJsonGuiTreeTab(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
			super(parent, obj);
			filterLevel = QuiltTreeWarningLevel.read(HELPER.expectString(obj, "level"));
			node = new QuiltStatusNode(null, HELPER.expectObject(obj, "node"));
		}

		@Override
		protected void write0(Map<String, LoaderValue> map) {
			map.put("level", lvf().string(filterLevel.lowerCaseName));
//			map.put("node", node)
		}

		@Override
		String syncType() {
			return "tree_tab";
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("level").value(filterLevel.lowerCaseName);
			writer.name("node");
			node.write(writer);
			writer.endObject();
		}
	}

	public static final class QuiltStatusNode {
		private QuiltStatusNode parent;

		public String name;

		/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
		 * {@link #setWarningLevel(QuiltTreeWarningLevel) warning level} is set to
		 * {@link QuiltTreeWarningLevel#NONE } */
		public String iconType = ICON_TYPE_DEFAULT;

		private QuiltTreeWarningLevel warningLevel = QuiltTreeWarningLevel.NONE;

		public boolean expandByDefault = false;

		public final List<QuiltStatusNode> children = new ArrayList<>();

		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;

		private QuiltStatusNode(QuiltStatusNode parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		private QuiltStatusNode(QuiltStatusNode parent, LoaderValue.LObject obj) throws IOException {
			this.parent = parent;
			name = HELPER.expectString(obj, "name");
			iconType = HELPER.expectString(obj, "icon");
			warningLevel = QuiltTreeWarningLevel.read(HELPER.expectString(obj, "level"));
			expandByDefault = HELPER.expectBoolean(obj, "expandByDefault");
			details = obj.containsKey("details") ? HELPER.expectString(obj, "details") : null;
			for (LoaderValue sub : HELPER.expectArray(obj, "children")) {
				children.add(new QuiltStatusNode(this, HELPER.expectObject(sub)));
			}
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("name").value(name);
			writer.name("icon").value(iconType);
			writer.name("level").value(warningLevel.lowerCaseName);
			writer.name("expandByDefault").value(expandByDefault);
			if (details != null) {
				writer.name("details").value(details);
			}
			writer.name("children").beginArray();

			for (QuiltStatusNode node : children) {
				node.write(writer);
			}

			writer.endArray();
			writer.endObject();
		}

		public void moveTo(QuiltStatusNode newParent) {
			parent.children.remove(this);
			this.parent = newParent;
			newParent.children.add(this);
		}

		public QuiltTreeWarningLevel getMaximumWarningLevel() {
			return warningLevel;
		}

		public void setWarningLevel(QuiltTreeWarningLevel level) {
			if (this.warningLevel == level || level == null) {
				return;
			}

			if (warningLevel.isHigherThan(level)) {
				// Reject high -> low level changes, since it's probably a mistake
			} else {
				if (parent != null && level.isHigherThan(parent.warningLevel)) {
					parent.setWarningLevel(level);
				}

				this.warningLevel = level;
			}
		}

		public void setError() {
			setWarningLevel(QuiltTreeWarningLevel.ERROR);
		}

		public void setWarning() {
			setWarningLevel(QuiltTreeWarningLevel.WARN);
		}

		public void setInfo() {
			setWarningLevel(QuiltTreeWarningLevel.INFO);
		}

		public QuiltStatusNode addChild(String string) {
			int indent = 0;
			QuiltTreeWarningLevel level = null;

			while (string.startsWith("\t")) {
				indent++;
				string = string.substring(1);
			}

			string = string.trim();

			if (string.length() > 1) {
				if (Character.isWhitespace(string.charAt(1))) {
					level = QuiltTreeWarningLevel.fromChar(string.charAt(0));

					if (level != null) {
						string = string.substring(2);
					}
				}
			}

			string = string.trim();
			String icon = "";

			if (string.length() > 3) {
				if ('$' == string.charAt(0)) {
					Pattern p = Pattern.compile("\\$([a-z.+-]+)\\$");
					Matcher match = p.matcher(string);
					if (match.find()) {
						icon = match.group(1);
						string = string.substring(icon.length() + 2);
					}
				}
			}

			string = string.trim();

			QuiltStatusNode to = this;

			for (; indent > 0; indent--) {
				if (to.children.isEmpty()) {
					QuiltStatusNode node = new QuiltStatusNode(to, "");
					to.children.add(node);
					to = node;
				} else {
					to = to.children.get(to.children.size() - 1);
				}

				to.expandByDefault = true;
			}

			QuiltStatusNode child = new QuiltStatusNode(to, string);
			child.setWarningLevel(level);
			child.iconType = icon;
			to.children.add(child);
			return child;
		}

		public QuiltStatusNode addException(Throwable exception) {
			return addException(
				this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, UnaryOperator.identity()
			);
		}

		public QuiltStatusNode addCleanedException(Throwable exception) {
			return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, e -> {
				// Remove some self-repeating exception traces from the tree
				// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
				Throwable cause;

				while ((cause = e.getCause()) != null) {
					if (e.getSuppressed().length > 0) {
						break;
					}

					String msg = e.getMessage();

					if (msg == null) {
						msg = e.getClass().getName();
					}

					if (!msg.equals(cause.getMessage()) && !msg.equals(cause.toString())) {
						break;
					}

					e = cause;
				}

				return e;
			});
		}

		private static QuiltStatusNode addException(QuiltStatusNode node, Set<Throwable> seen, Throwable exception,
			UnaryOperator<Throwable> filter) {
			if (!seen.add(exception)) {
				return node;
			}

			exception = filter.apply(exception);
			QuiltStatusNode sub = node.addExceptionNode(exception);

			for (Throwable t : exception.getSuppressed()) {
				addException(sub, seen, t, filter);
			}

			if (exception.getCause() != null) {
				addException(sub, seen, exception.getCause(), filter);
			}

			return sub;
		}

		private QuiltStatusNode addExceptionNode(Throwable exception) {
			String msg;

			if (exception instanceof FormattedException) {
				msg = Objects.toString(exception.getMessage());
			} else if (exception.getMessage() == null || exception.getMessage().isEmpty()) {
				msg = exception.toString();
			} else {
				msg = String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage());
			}

			String[] lines = msg.split("\n");

			QuiltStatusNode sub = new QuiltStatusNode(this, lines[0]);
			children.add(sub);
			sub.setError();
			sub.expandByDefault = true;

			for (int i = 1; i < lines.length; i++) {
				sub.addChild(lines[i]);
			}

			return sub;
		}

		/** If this node has one child then it merges the child node into this one. */
		public void mergeWithSingleChild(String join) {
			if (children.size() != 1) {
				return;
			}

			QuiltStatusNode child = children.remove(0);
			name += join + child.name;

			for (QuiltStatusNode cc : child.children) {
				cc.parent = this;
				children.add(cc);
			}

			child.children.clear();
		}

		public void mergeSingleChildFilePath(String folderType) {
			if (!iconType.equals(folderType)) {
				return;
			}

			while (children.size() == 1 && children.get(0).iconType.equals(folderType)) {
				mergeWithSingleChild("/");
			}

			children.sort((a, b) -> a.name.compareTo(b.name));
			mergeChildFilePaths(folderType);
		}

		public void mergeChildFilePaths(String folderType) {
			for (QuiltStatusNode node : children) {
				node.mergeSingleChildFilePath(folderType);
			}
		}

		public QuiltStatusNode getFileNode(String file, String folderType, String fileType) {
			QuiltStatusNode fileNode = this;

			pathIteration: for (String s : file.split("/")) {
				if (s.isEmpty()) {
					continue;
				}

				for (QuiltStatusNode c : fileNode.children) {
					if (c.name.equals(s)) {
						fileNode = c;
						continue pathIteration;
					}
				}

				if (fileNode.iconType.equals(QuiltJsonGui.ICON_TYPE_DEFAULT)) {
					fileNode.iconType = folderType;
				}

				fileNode = fileNode.addChild(s);
			}

			fileNode.iconType = fileType;
			return fileNode;
		}
	}
}
