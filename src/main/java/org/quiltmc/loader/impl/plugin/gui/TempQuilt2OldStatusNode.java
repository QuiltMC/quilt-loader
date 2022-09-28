/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.impl.plugin.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.impl.gui.QuiltJsonGui;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;

public class TempQuilt2OldStatusNode implements PluginGuiTreeNode {

	final TempQuilt2OldStatusNode parent;

	Text text = Text.EMPTY;
	String sortPrefix = "";
	Throwable exception;

	WarningLevel directLevel = WarningLevel.NONE;
	WarningLevel cachedLevel = WarningLevel.NONE;

	PluginGuiIcon mainIcon = GuiManagerImpl.ICON_NULL;
	PluginGuiIcon subIcon = null;

	final List<TempQuilt2OldStatusNode> childrenByAddition, childrenByAlphabetical;

	Boolean expandByDefault = null;

	public TempQuilt2OldStatusNode(TempQuilt2OldStatusNode parent) {
		this.parent = parent;
		childrenByAddition = new ArrayList<>();
		childrenByAlphabetical = new ArrayList<>();
	}

	public void toNode(QuiltJsonGui.QuiltStatusNode node, boolean debug) {
		node.name = text.toString();

		if (mainIcon != GuiManagerImpl.ICON_NULL) {
			node.iconType = mainIcon.tempToStatusNodeStr() + (subIcon == null ? "" : ("+" + subIcon
				.tempToStatusNodeStr()));
		} else if (subIcon != null) {
			node.iconType = subIcon.tempToStatusNodeStr();
		}

		node.setWarningLevel(QuiltTreeWarningLevel.fromApiLevel(directLevel));

		if (expandByDefault == null) {
			node.expandByDefault = QuiltTreeWarningLevel.fromApiLevel(cachedLevel).isAtLeast(QuiltTreeWarningLevel.CONCERN);
		} else {
			node.expandByDefault = expandByDefault;
		}

		for (TempQuilt2OldStatusNode n : childrenByAddition) {
			if (debug || n.directLevel != WarningLevel.DEBUG_ONLY) {
				n.toNode(node.addChild(n.text.toString()), debug);
			}
		}

		childrenByAlphabetical.sort(Comparator.comparing((TempQuilt2OldStatusNode a) -> a.sortPrefix).thenComparing(a -> a.text.toString()));

		for (TempQuilt2OldStatusNode n : childrenByAlphabetical) {
			if (debug || n.directLevel != WarningLevel.DEBUG_ONLY) {
				n.toNode(node.addChild(n.text.toString()), debug);
			}
		}
	}

	@Override
	public PluginGuiManager manager() {
		return GuiManagerImpl.INSTANCE;
	}

	@Override
	public @Nullable PluginGuiTreeNode parent() {
		return parent;
	}

	@Override
	public PluginGuiTreeNode addChild(SortOrder sortOrder) {
		return addChild(Text.EMPTY, sortOrder);
	}

	@Override
	public PluginGuiTreeNode addChild(Text text, SortOrder sortOrder) {
		TempQuilt2OldStatusNode child = new TempQuilt2OldStatusNode(this);
		child.text = text;
		switch (sortOrder) {
			case ADDITION_ORDER: {
				childrenByAddition.add(child);
				break;
			}
			case ALPHABETICAL_ORDER: {
				childrenByAlphabetical.add(child);
				break;
			}
			default: {
				throw new IllegalStateException("Unknown SortOrder " + sortOrder);
			}
		}
		return child;
	}

	@Override
	public Text text() {
		return text;
	}

	@Override
	public PluginGuiTreeNode text(Text text) {
		this.text = text;
		return this;
	}

	@Override
	public String sortPrefix() {
		return sortPrefix;
	}

	@Override
	public TempQuilt2OldStatusNode sortPrefix(String prefix) {
		this.sortPrefix = prefix;
		return this;
	}

	@Override
	public TempQuilt2OldStatusNode setDirectLevel(WarningLevel level) {
		this.directLevel = level;
		this.resetCachedLevel();
		return this;
	}

	private void resetCachedLevel() {
		WarningLevel max = directLevel;

		for (TempQuilt2OldStatusNode c : childrenByAddition) {
			if (c.getMaximumLevel().ordinal() < max.ordinal()) {
				max = c.getMaximumLevel();
			}
		}

		for (TempQuilt2OldStatusNode c : childrenByAlphabetical) {
			if (c.getMaximumLevel().ordinal() < max.ordinal()) {
				max = c.getMaximumLevel();
			}
		}

		cachedLevel = max;

		if (parent != null) {
			parent.resetCachedLevel();
		}
	}

	@Override
	public WarningLevel getDirectLevel() {
		return directLevel;
	}

	@Override
	public WarningLevel getMaximumLevel() {
		return cachedLevel;
	}

	@Override
	public TempQuilt2OldStatusNode setException(Throwable exception) {
		this.exception = exception;
		return this;
	}

	@Override
	public PluginGuiIcon mainIcon() {
		return mainIcon;
	}

	@Override
	public TempQuilt2OldStatusNode mainIcon(PluginGuiIcon icon) {
		mainIcon = icon;
		return this;
	}

	@Override
	public @Nullable PluginGuiIcon subIcon() {
		return subIcon;
	}

	@Override
	public TempQuilt2OldStatusNode subIcon(PluginGuiIcon icon) {
		this.subIcon = icon;
		return this;
	}

	@Override
	public void expandByDefault(boolean autoCollapse) {
		this.expandByDefault = autoCollapse;
	}
}
