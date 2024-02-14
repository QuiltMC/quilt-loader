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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon.SubIconPosition;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class QuiltStatusNode extends QuiltGuiSyncBase implements QuiltTreeNode, PluginGuiTreeNode {

	interface TreeNodeListener extends Listener {
		default void onTextChanged() {}

		default void onIconChanged() {}

		default void onLevelChanged() {}

		default void onMaxLevelChanged() {}

		default void onChildAdded(QuiltStatusNode child) {}
	}

	private QuiltLoaderText apiText = QuiltLoaderText.EMPTY;
	String text = "";

	PluginIconImpl icon = new PluginIconImpl();

	QuiltWarningLevel level = QuiltWarningLevel.NONE;
	QuiltWarningLevel maxLevel = QuiltWarningLevel.NONE;
	QuiltWarningLevel autoExpandLevel = QuiltWarningLevel.WARN;

	private String sortPrefix = "";

	final List<QuiltStatusNode> childNodesByAddition = new ArrayList<>();
	final List<QuiltStatusNode> childNodesByAlphabetical = new ArrayList<>();

	/** Extra text for more information. Lines should be separated by "\n". */
	public String details;

	QuiltStatusNode(QuiltGuiSyncBase parent) {
		super(parent);
	}

	QuiltStatusNode(QuiltGuiSyncBase parent, LoaderValue.LObject obj) throws IOException {
		super(parent, obj);
		text = HELPER.expectString(obj, "name");
		icon = readChild(HELPER.expectValue(obj, "icon"), PluginIconImpl.class);
		level = HELPER.expectEnum(QuiltWarningLevel.class, obj, "level");
		maxLevel = HELPER.expectEnum(QuiltWarningLevel.class, obj, "maxLevel");
		autoExpandLevel = HELPER.expectEnum(QuiltWarningLevel.class, obj, "autoExpandLevel");
		details = obj.containsKey("details") ? HELPER.expectString(obj, "details") : null;
		for (LoaderValue sub : HELPER.expectArray(obj, "children_by_addition")) {
			childNodesByAddition.add(readChild(sub, QuiltStatusNode.class));
		}
		for (LoaderValue sub : HELPER.expectArray(obj, "children_by_alphabetical")) {
			childNodesByAlphabetical.add(readChild(sub, QuiltStatusNode.class));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("name", lvf().string(text));
		map.put("icon", writeChild(icon));
		map.put("level", lvf().string(level.name()));
		map.put("maxLevel", lvf().string(maxLevel.name()));
		map.put("autoExpandLevel", lvf().string(autoExpandLevel.name()));
		if (details != null) {
			map.put("details", lvf().string(details));
		}
		map.put("children_by_addition", lvf().array(write(childNodesByAddition)));
		map.put("children_by_alphabetical", lvf().array(write(childNodesByAlphabetical)));
	}

	@Override
	String syncType() {
		return "tree_node";
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "set_icon": {
				this.icon = readChild(HELPER.expectValue(data, "icon"), PluginIconImpl.class);
				invokeListeners(TreeNodeListener.class, TreeNodeListener::onIconChanged);
				break;
			}
			case "set_text": {
				this.text = HELPER.expectString(data, "text");
				invokeListeners(TreeNodeListener.class, TreeNodeListener::onTextChanged);
				break;
			}
			case "set_level": {
				this.level = HELPER.expectEnum(QuiltWarningLevel.class, data, "level");
				invokeListeners(TreeNodeListener.class, TreeNodeListener::onLevelChanged);
				break;
			}
			case "set_max_level": {
				this.maxLevel = HELPER.expectEnum(QuiltWarningLevel.class, data, "max_level");
				invokeListeners(TreeNodeListener.class, TreeNodeListener::onMaxLevelChanged);
				break;
			}
			default: {
				super.handleUpdate(name, data);
			}
		}
	}

	@Override
	public QuiltStatusNode parent() {
		return parent instanceof QuiltStatusNode ? (QuiltStatusNode) parent : null;
	}

	@Override
	public QuiltLoaderIcon icon() {
		return icon;
	}

	@Override
	public QuiltTreeNode icon(QuiltLoaderIcon icon) {
		this.icon = PluginIconImpl.fromApi(icon);
		if (this.icon == null) {
			this.icon = new PluginIconImpl();
		}
		invokeListeners(TreeNodeListener.class, TreeNodeListener::onIconChanged);
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("icon", writeChild(this.icon));
			sendUpdate("set_icon", lvf().object(map));
		}
		return this;
	}

	@Override
	public QuiltLoaderText text() {
		return apiText;
	}

	@Override
	public QuiltStatusNode text(QuiltLoaderText text) {
		apiText = Objects.requireNonNull(text);
		this.text = text.toString();
		QuiltStatusNode p = parent();
		if (p != null) {
			p.sortChildren();
		}
		invokeListeners(TreeNodeListener.class, TreeNodeListener::onTextChanged);
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("text", lvf().string(this.text));
			sendUpdate("set_text", lvf().object(map));
		}
		return this;
	}

	@Override
	public QuiltWarningLevel level() {
		return level;
	}

	@Override
	public QuiltTreeNode level(QuiltWarningLevel level) {
		this.level = Objects.requireNonNull(level);
		invokeListeners(TreeNodeListener.class, TreeNodeListener::onLevelChanged);
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("level", lvf().string(this.level.name()));
			sendUpdate("set_level", lvf().object(map));
		}
		recomputeMaxLevel();
		return this;
	}

	private void recomputeMaxLevel() {
		QuiltWarningLevel oldMaxLevel = maxLevel;
		maxLevel = level;
		for (QuiltStatusNode child : childIterable()) {
			maxLevel = QuiltWarningLevel.getHighest(maxLevel, child.maxLevel);
		}
		if (maxLevel != oldMaxLevel) {
			if (shouldSendUpdates()) {
				Map<String, LoaderValue> map = new HashMap<>();
				map.put("max_level", lvf().string(this.maxLevel.name()));
				sendUpdate("set_max_level", lvf().object(map));
			}
			if (parent instanceof QuiltStatusNode) {
				((QuiltStatusNode) parent).recomputeMaxLevel();
			}
		}
	}

	@Override
	public QuiltWarningLevel maximumLevel() {
		return maxLevel;
	}

	@Override
	public int countAtLevel(QuiltWarningLevel level) {
		int count = this.level == level ? 1 : 0;
		for (QuiltStatusNode node : childIterable()) {
			count += node.countAtLevel(level);
		}
		return count;
	}

	@Override
	public QuiltTreeNode autoExpandLevel(QuiltWarningLevel level) {
		autoExpandLevel = level;
		return this;
	}

	public boolean getExpandByDefault() {
		return autoExpandLevel.ordinal() >= maxLevel.ordinal();
	}

	public void setExpandByDefault(boolean expandByDefault) {
		if (expandByDefault) {
			autoExpandLevel(QuiltWarningLevel.values()[QuiltWarningLevel.values().length - 1]);
		}
	}

	@Deprecated
	public void setError() {
		level(QuiltWarningLevel.ERROR);
	}

	@Deprecated
	public void setWarning() {
		level(QuiltWarningLevel.WARN);
	}

	@Deprecated
	public void setInfo() {
		level(QuiltWarningLevel.INFO);
	}

	@Override
	public QuiltStatusNode addChild(QuiltTreeNode.SortOrder sortOrder) {
		QuiltStatusNode child = new QuiltStatusNode(this);
		if (sortOrder == QuiltTreeNode.SortOrder.ADDITION_ORDER) {
			childNodesByAddition.add(child);
		} else {
			childNodesByAlphabetical.add(child);
		}
		invokeListeners(TreeNodeListener.class, l -> l.onChildAdded(child));
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("sort_order", lvf().string(sortOrder.name()));
			map.put("child", writeChild(child));
			sendUpdate("add_child", lvf().object(map));
		}
		return child;
	}

	@Override
	public QuiltStatusNode addChild() {
		return addChild(QuiltTreeNode.SortOrder.ADDITION_ORDER);
	}

	@Override
	public QuiltStatusNode addChild(QuiltLoaderText text) {
		QuiltStatusNode child = addChild();
		child.text(text);
		return child;
	}

	@Override
	public QuiltStatusNode addChild(QuiltLoaderText text, QuiltTreeNode.SortOrder sortOrder) {
		return addChild(sortOrder).text(text);
	}

	void forEachChild(Consumer<? super QuiltStatusNode> consumer) {
		childNodesByAddition.forEach(consumer);
		childNodesByAlphabetical.forEach(consumer);
	}

	Iterable<QuiltStatusNode> childIterable() {
		return () -> new Iterator<QuiltStatusNode>() {
			final Iterator<QuiltStatusNode> first = childNodesByAddition.iterator();
			final Iterator<QuiltStatusNode> second = childNodesByAlphabetical.iterator();

			@Override
			public boolean hasNext() {
				return first.hasNext() || second.hasNext();
			}

			@Override
			public QuiltStatusNode next() {
				if (first.hasNext()) {
					return first.next();
				} else {
					return second.next();
				}
			}
		};
	}

	@Override
	public String sortPrefix() {
		return sortPrefix;
	}

	@Override
	public QuiltStatusNode sortPrefix(String sortPrefix) {
		if (sortPrefix == null) {
			sortPrefix = "";
		}
		if (this.sortPrefix.equals(sortPrefix)) {
			return this;
		}
		this.sortPrefix = sortPrefix;
		QuiltStatusNode p = parent();
		if (p != null) {
			p.sortChildren();
		}
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("sort_prefix", lvf().string(sortPrefix));
			sendUpdate("set_sort_prefix", lvf().object(map));
		}
		return this;
	}

	private void sortChildren() {
		childNodesByAlphabetical.sort((a, b) -> {
			int cmp = a.sortPrefix.compareTo(b.sortPrefix);
			if (cmp != 0) {
				return cmp;
			}
			return a.text.compareTo(b.text);
		});
	}

	// Deprecated PluginGuiTreeNode methods

	@Override
	@Deprecated
	public QuiltTreeNode getNew() {
		return this;
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode addChild(PluginGuiTreeNode.SortOrder sortOrder) {
		return addChild(QuiltLoaderText.EMPTY, sortOrder);
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode addChild(QuiltLoaderText text, PluginGuiTreeNode.SortOrder sortOrder) {
		QuiltTreeNode.SortOrder newOrder = sortOrder == PluginGuiTreeNode.SortOrder.ADDITION_ORDER
			? QuiltTreeNode.SortOrder.ADDITION_ORDER
			: QuiltTreeNode.SortOrder.ALPHABETICAL_ORDER;
		return addChild(newOrder);
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode setDirectLevel(WarningLevel level) {
		level(fromOldLevel(level));
		return this;
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode setException(Throwable exception) {
		return this;
	}

	@Deprecated
	private static QuiltWarningLevel fromOldLevel(WarningLevel level) {
		switch (level) {
			case CONCERN:
				return QuiltWarningLevel.CONCERN;
			case DEBUG_ONLY:
				return QuiltWarningLevel.DEBUG_ONLY;
			case ERROR:
				return QuiltWarningLevel.ERROR;
			case FATAL:
				return QuiltWarningLevel.FATAL;
			case INFO:
				return QuiltWarningLevel.INFO;
			case NONE:
				return QuiltWarningLevel.NONE;
			case WARN:
				return QuiltWarningLevel.WARN;
			default:
				throw new IllegalStateException("Unknown WarningLevel " + level);
		}
	}

	@Deprecated
	private WarningLevel toOldLevel(QuiltWarningLevel level) {
		switch (level) {
			case CONCERN:
				return WarningLevel.CONCERN;
			case DEBUG_ONLY:
				return WarningLevel.DEBUG_ONLY;
			case ERROR:
				return WarningLevel.ERROR;
			case FATAL:
				return WarningLevel.FATAL;
			case INFO:
				return WarningLevel.INFO;
			case NONE:
				return WarningLevel.NONE;
			case WARN:
				return WarningLevel.WARN;
			default:
				throw new IllegalStateException("Unknown QuiltWarningLevel " + level);
		}
	}

	@Override
	@Deprecated
	public WarningLevel getDirectLevel() {
		return toOldLevel(level());
	}

	@Override
	@Deprecated
	public WarningLevel getMaximumLevel() {
		return toOldLevel(maximumLevel());
	}

	@Override
	@Deprecated
	public int countOf(WarningLevel level) {
		return countAtLevel(fromOldLevel(level));
	}

	@Override
	@Deprecated
	public QuiltLoaderIcon mainIcon() {
		QuiltLoaderIcon i = icon();
		if (i == null) {
			return QuiltLoaderGui.iconTreeDot();
		}
		for (SubIconPosition pos : SubIconPosition.values()) {
			if (i.getDecoration(pos) != null) {
				i = i.withDecoration(pos, null);
			}
		}
		return i;
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode mainIcon(QuiltLoaderIcon icon) {
		for (SubIconPosition pos : SubIconPosition.values()) {
			icon = icon.withDecoration(pos, icon().getDecoration(pos));
		}
		icon(icon);
		return this;
	}

	@Override
	@Deprecated
	public @Nullable QuiltLoaderIcon subIcon() {
		return icon.getDecoration(SubIconPosition.BOTTOM_RIGHT);
	}

	@Override
	@Deprecated
	public PluginGuiTreeNode subIcon(QuiltLoaderIcon subIcon) {
		icon(icon().withDecoration(SubIconPosition.BOTTOM_RIGHT, subIcon));
		return this;
	}

	@Override
	@Deprecated
	public void expandByDefault(boolean autoCollapse) {
		autoExpandLevel(autoCollapse ? QuiltWarningLevel.NONE : QuiltWarningLevel.FATAL);
	}

	@Override
	@Deprecated
	public PluginGuiManager manager() {
		return GuiManagerImpl.MANAGER;
	}
}
