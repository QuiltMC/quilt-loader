/*
 * Copyright 2016 FabricMC
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuiltStatusTree {
	public enum FabricTreeWarningLevel {
		ERROR,
		WARN,
		INFO,
		NONE;

		public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

		public boolean isHigherThan(FabricTreeWarningLevel other) {
			return ordinal() < other.ordinal();
		}

		public boolean isAtLeast(FabricTreeWarningLevel other) {
			return ordinal() <= other.ordinal();
		}

		public static FabricTreeWarningLevel getHighest(FabricTreeWarningLevel a, FabricTreeWarningLevel b) {
			return a.isHigherThan(b) ? a : b;
		}
	}

	public enum FabricBasicButtonType {
		/** Sends the status message to the main application, then disables itself. */
		CLICK_ONCE;
	}

	/** No icon is displayed. */
	public static final String ICON_TYPE_DEFAULT = "";

	/** Generic folder. */
	public static final String ICON_TYPE_FOLDER = "folder";

	/** Generic (unknown contents) file. */
	public static final String ICON_TYPE_UNKNOWN_FILE = "file";

	/** Generic non-Fabric jar file. */
	public static final String ICON_TYPE_JAR_FILE = "jar";

	/** Generic Fabric-related jar file. */
	public static final String ICON_TYPE_FABRIC_JAR_FILE = "jar+fabric";

	/** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_FABRIC = "fabric";

	/** Generic JSON file. */
	public static final String ICON_TYPE_JSON = "json";

	/** A file called "fabric.mod.json". */
	public static final String ICON_TYPE_FABRIC_JSON = "json+fabric";

	/** Java bytecode class file. */
	public static final String ICON_TYPE_JAVA_CLASS = "java_class";

	/** A folder inside of a Java JAR. */
	public static final String ICON_TYPE_PACKAGE = "package";

	/** A folder that contains Java class files. */
	public static final String ICON_TYPE_JAVA_PACKAGE = "java_package";

	/** A tick symbol, used to indicate that something matched. */
	public static final String ICON_TYPE_TICK = "tick";

	/** A cross symbol, used to indicate that something didn't match (although it's not an error). Used as the opposite
	 * of {@link #ICON_TYPE_TICK} */
	public static final String ICON_TYPE_LESSER_CROSS = "lesser_cross";

	public final List<QuiltStatusTab> tabs = new ArrayList<>();
	public final List<QuiltStatusButton> buttons = new ArrayList<>();

	public String mainText = null;

	public QuiltStatusTab addTab(String name) {
		QuiltStatusTab tab = new QuiltStatusTab(name);
		tabs.add(tab);
		return tab;
	}

	public QuiltStatusButton addButton(String text) {
		QuiltStatusButton button = new QuiltStatusButton(text);
		buttons.add(button);
		return button;
	}

	public static final class QuiltStatusButton {
		public final String text;
		public boolean shouldClose, shouldContinue;

		public QuiltStatusButton(String text) {
			this.text = text;
		}

		public QuiltStatusButton makeClose() {
			shouldClose = true;
			return this;
		}

		public QuiltStatusButton makeContinue() {
			this.shouldContinue = true;
			return this;
		}
	}

	public static final class QuiltStatusTab {
		public final QuiltStatusNode node;

		/** The minimum warning level to display for this tab. */
		public FabricTreeWarningLevel filterLevel = FabricTreeWarningLevel.NONE;

		public QuiltStatusTab(String name) {
			this.node = new QuiltStatusNode(null, name);
		}

		public QuiltStatusNode addChild(String name) {
			return node.addChild(name);
		}
	}

	public static final class QuiltStatusNode {
		private QuiltStatusNode parent;

		public String name;

		/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
		 * {@link #setWarningLevel(FabricTreeWarningLevel) warning level} is set to
		 * {@link FabricTreeWarningLevel#NONE } */
		public String iconType = ICON_TYPE_DEFAULT;

		private FabricTreeWarningLevel warningLevel = FabricTreeWarningLevel.NONE;

		public boolean expandByDefault = false;

		public final List<QuiltStatusNode> children = new ArrayList<>();

		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;

		private QuiltStatusNode(QuiltStatusNode parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		public void moveTo(QuiltStatusNode newParent) {
			parent.children.remove(this);
			this.parent = newParent;
			newParent.children.add(this);
		}

		public FabricTreeWarningLevel getMaximumWarningLevel() {
			return warningLevel;
		}

		public void setWarningLevel(FabricTreeWarningLevel level) {
			if (this.warningLevel == level) {
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
			setWarningLevel(FabricTreeWarningLevel.ERROR);
		}

		public void setWarning() {
			setWarningLevel(FabricTreeWarningLevel.WARN);
		}

		public void setInfo() {
			setWarningLevel(FabricTreeWarningLevel.INFO);
		}

		private QuiltStatusNode addChild(String string) {
			if (string.startsWith("\t")) {
				if (children.size() == 0) {
					QuiltStatusNode rootChild = new QuiltStatusNode(this, "");
					children.add(rootChild);
				}
				QuiltStatusNode lastChild = children.get(children.size() - 1);
				lastChild.addChild(string.substring(1));
				lastChild.expandByDefault = true;
				return lastChild;
			} else {
				QuiltStatusNode child = new QuiltStatusNode(this, cleanForNode(string));
				children.add(child);
				return child;
			}
		}

		private String cleanForNode(String string) {
			string = string.trim();
			if (string.length() > 1) {
				if (string.startsWith("-")) {
					string = string.substring(1);
					string = string.trim();
				}
			}
			return string;
		}

		public QuiltStatusNode addException(Throwable exception) {
			QuiltStatusNode sub = new QuiltStatusNode(this, "...");
			children.add(sub);

			sub.setError();
			String msg = exception.getMessage();
			String[] lines = (msg == null ? exception.toString() : msg).split("\n");

			if (lines.length == 0) {
				sub.name = exception.toString();
			} else {
				sub.name = lines[0];

				for (int i = 1; i < lines.length; i++) {
					sub.addChild(lines[i]);
				}
			}

			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			sub.details = sw.toString();

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

				if (fileNode.iconType.equals(QuiltStatusTree.ICON_TYPE_DEFAULT)) {
					fileNode.iconType = folderType;
				}

				fileNode = fileNode.addChild(s);
			}

			fileNode.iconType = fileType;
			return fileNode;
		}
	}
}
