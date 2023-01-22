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

import java.awt.Desktop;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.impl.FormattedException;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltJsonGui {
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

		static QuiltTreeWarningLevel read(JsonReader reader) throws IOException {
			String string = reader.nextString();
			if (string.isEmpty()) {
				return NONE;
			}
			QuiltTreeWarningLevel level = nameToValue.get(string);
			if (level != null) {
				return level;
			} else {
				throw new IOException("Expected a valid FabricTreeWarningLevel, but got '" + string + "'");
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

	public final String title;
	public final String mainText;
	private final List<NavigableMap<Integer, BufferedImage>> customIcons = new ArrayList<>();

	public String messagesTabName = "_MESSAGES_";
	public final List<QuiltJsonGuiMessage> messages = new ArrayList<>();
	public final List<QuiltJsonGuiTreeTab> tabs = new ArrayList<>();
	public final List<QuiltJsonButton> buttons = new ArrayList<>();

	public QuiltJsonGui(String title, String mainText) {
		this.title = title;
		this.mainText = mainText == null ? "" : mainText;
	}

	public int allocateCustomIcon(BufferedImage image) {
		return allocateCustomIcon(Collections.singletonMap(image.getWidth(), image));
	}

	public int allocateCustomIcon(Map<Integer, BufferedImage> imageSizes) {
		customIcons.add(new TreeMap<>(imageSizes));
		return customIcons.size() - 1;
	}

	public NavigableMap<Integer, BufferedImage> getCustomIcon(int index) {
		if (index < 0 || index >= customIcons.size()) {
			return Collections.emptyNavigableMap();
		}
		return Collections.unmodifiableNavigableMap(customIcons.get(index));
	}

	public int getCustomIconCount() {
		return customIcons.size();
	}

	public QuiltJsonGuiTreeTab addTab(String name) {
		QuiltJsonGuiTreeTab tab = new QuiltJsonGuiTreeTab(name);
		tabs.add(tab);
		return tab;
	}

	public QuiltJsonButton addButton(String text, QuiltBasicButtonAction action) {
		return addButton(text, action.defaultIcon, action);
	}

	public QuiltJsonButton addButton(String text, String icon, QuiltBasicButtonAction action) {
		QuiltJsonButton button = new QuiltJsonButton(text, icon, action);
		buttons.add(button);
		return button;
	}

	public QuiltJsonGui(JsonReader reader) throws IOException {
		reader.beginObject();
		expectName(reader, "title");
		title = reader.nextString();
		// As we write ourselves we mandate the order
		// (This also makes everything a lot simpler)
		expectName(reader, "mainText");
		mainText = reader.nextString();

		expectName(reader, "message_tab_name");
		messagesTabName = reader.nextString();
		expectName(reader, "messages");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			messages.add(new QuiltJsonGuiMessage(reader));
		}
		reader.endArray();

		expectName(reader, "tabs");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			tabs.add(new QuiltJsonGuiTreeTab(reader));
		}
		reader.endArray();

		expectName(reader, "buttons");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			buttons.add(new QuiltJsonButton(reader));
		}
		reader.endArray();

		expectName(reader, "custom_icons");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			reader.beginObject();
			NavigableMap<Integer, BufferedImage> map = new TreeMap<>();
			while (reader.peek() != JsonToken.END_OBJECT) {
				int size = Integer.parseInt(reader.nextName());
				String base64 = reader.nextString();
				byte[] bytes = Base64.getDecoder().decode(base64);
				map.put(size, ImageIO.read(new ByteArrayInputStream(bytes)));
			}
			customIcons.add(map);
			reader.endObject();
		}
		reader.endArray();

		reader.endObject();
	}

	/** Writes this tree out as a single json object. */
	public void write(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("title").value(title);
		writer.name("mainText").value(mainText);
		writer.name("message_tab_name").value(messagesTabName);
		writer.name("messages").beginArray();
		for (QuiltJsonGuiMessage sub : messages) {
			sub.write(writer);
		}
		writer.endArray();
		writer.name("tabs").beginArray();
		for (QuiltJsonGuiTreeTab tab : tabs) {
			tab.write(writer);
		}
		writer.endArray();
		writer.name("buttons").beginArray();
		for (QuiltJsonButton button : buttons) {
			button.write(writer);
		}
		writer.endArray();

		writer.name("custom_icons").beginArray();
		for (Map<Integer, BufferedImage> map : customIcons) {
			writer.beginObject();
			for (Entry<Integer, BufferedImage> entry : map.entrySet()) {
				writer.name(entry.getKey().toString());
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(entry.getValue(), "png", baos);
				writer.value(Base64.getEncoder().encodeToString(baos.toByteArray()));
			}
			writer.endObject();
		}
		writer.endArray();
		writer.endObject();
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

	public static final class QuiltJsonButton {
		public final String text, icon;
		public final QuiltBasicButtonAction action;
		public final Map<String, String> arguments = new HashMap<>();

		/** Only used for {@link QuiltBasicButtonAction#RETURN_SIGNAL_ONCE} and
		 * {@link QuiltBasicButtonAction#RETURN_SIGNAL_MANY} */
		Runnable returnSignalAction;

		public QuiltJsonButton(String text, String icon, QuiltBasicButtonAction action) {
			this.text = text;
			this.icon = icon;
			this.action = action;
		}

		QuiltJsonButton(JsonReader reader) throws IOException {
			reader.beginObject();
			expectName(reader, "text");
			text = reader.nextString();
			expectName(reader, "icon");
			icon = reader.nextString();
			expectName(reader, "action");
			action = QuiltBasicButtonAction.valueOf(reader.nextString());
			expectName(reader, "arguments");
			reader.beginObject();
			while (reader.peek() != JsonToken.END_OBJECT) {
				arguments.put(reader.nextName(), reader.nextString());
			}
			reader.endObject();
			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("text").value(text);
			writer.name("icon").value(icon);
			writer.name("action").value(action.name());
			writer.name("arguments").beginObject();
			for (Entry<String, String> entry : arguments.entrySet()) {
				writer.name(entry.getKey()).value(entry.getValue());
			}
			writer.endObject();
			writer.endObject();
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
	}

	public static final class QuiltJsonGuiMessage {

		/** The icon type. */
		public String iconType = ICON_TYPE_DEFAULT;

		public String title;

		public final List<String> description = new ArrayList<>();
		public final List<String> additionalInfo = new ArrayList<>();

		public final List<QuiltJsonButton> buttons = new ArrayList<>();

		public String subMessageHeader = "";
		public final List<QuiltJsonGuiMessage> subMessages = new ArrayList<>();

		public QuiltJsonGuiMessage() {

		}

		public QuiltJsonGuiMessage(JsonReader reader) throws IOException {
			reader.beginObject();
			expectName(reader, "title");
			title = reader.nextString();

			expectName(reader, "icon");
			iconType = reader.nextString();

			expectName(reader, "description");
			reader.beginArray();
			while (reader.peek() != JsonToken.END_ARRAY) {
				description.add(reader.nextString());
			}
			reader.endArray();

			expectName(reader, "info");
			reader.beginArray();
			while (reader.peek() != JsonToken.END_ARRAY) {
				additionalInfo.add(reader.nextString());
			}
			reader.endArray();

			expectName(reader, "buttons");
			reader.beginArray();
			while (reader.peek() != JsonToken.END_ARRAY) {
				buttons.add(new QuiltJsonButton(reader));
			}
			reader.endArray();

			expectName(reader, "sub_message_header");
			subMessageHeader = reader.nextString();

			expectName(reader, "sub_messages");
			reader.beginArray();
			while (reader.peek() != JsonToken.END_ARRAY) {
				subMessages.add(new QuiltJsonGuiMessage(reader));
			}
			reader.endArray();

			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();

			writer.name("title");
			writer.value(title);

			writer.name("icon");
			writer.value(iconType);

			writer.name("description");
			writer.beginArray();
			for (String desc : description) {
				writer.value(desc);
			}
			writer.endArray();

			writer.name("info");
			writer.beginArray();
			for (String info : additionalInfo) {
				writer.value(info);
			}
			writer.endArray();

			writer.name("buttons");
			writer.beginArray();
			for (QuiltJsonButton btn : buttons) {
				btn.write(writer);
			}
			writer.endArray();

			writer.name("sub_message_header");
			writer.value(subMessageHeader);

			writer.name("sub_messages");
			writer.beginArray();
			for (QuiltJsonGuiMessage sub : subMessages) {
				sub.write(writer);
			}
			writer.endArray();

			writer.endObject();
		}
	}

	public static final class QuiltJsonGuiTreeTab {
		public final QuiltStatusNode node;

		/** The minimum warning level to display for this tab. */
		public QuiltTreeWarningLevel filterLevel = QuiltTreeWarningLevel.NONE;

		public QuiltJsonGuiTreeTab(String name) {
			this.node = new QuiltStatusNode(null, name);
		}

		public QuiltStatusNode addChild(String name) {
			return node.addChild(name);
		}

		QuiltJsonGuiTreeTab(JsonReader reader) throws IOException {
			reader.beginObject();
			expectName(reader, "level");
			filterLevel = QuiltTreeWarningLevel.read(reader);
			expectName(reader, "node");
			node = new QuiltStatusNode(null, reader);
			reader.endObject();
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

		private QuiltStatusNode(QuiltStatusNode parent, JsonReader reader) throws IOException {
			this.parent = parent;
			reader.beginObject();
			expectName(reader, "name");
			name = reader.nextString();
			expectName(reader, "icon");
			iconType = reader.nextString();
			expectName(reader, "level");
			warningLevel = QuiltTreeWarningLevel.read(reader);
			expectName(reader, "expandByDefault");
			expandByDefault = reader.nextBoolean();
			expectName(reader, "details");
			details = readStringOrNull(reader);
			expectName(reader, "children");
			reader.beginArray();

			while (reader.peek() != JsonToken.END_ARRAY) {
				children.add(new QuiltStatusNode(this, reader));
			}

			reader.endArray();
			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("name").value(name);
			writer.name("icon").value(iconType);
			writer.name("level").value(warningLevel.lowerCaseName);
			writer.name("expandByDefault").value(expandByDefault);
			writer.name("details").value(details);
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
				// Just because I haven't written the back-fill revalidation for this
				throw new Error("Why would you set the warning level multiple times?");
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
