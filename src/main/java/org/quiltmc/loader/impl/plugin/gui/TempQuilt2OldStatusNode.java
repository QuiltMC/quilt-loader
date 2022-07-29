package org.quiltmc.loader.impl.plugin.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.impl.gui.QuiltStatusTree;
import org.quiltmc.loader.impl.gui.QuiltStatusTree.FabricTreeWarningLevel;

public class TempQuilt2OldStatusNode implements PluginGuiTreeNode {

	final TempQuilt2OldStatusNode parent;

	String text = "";
	String sortPrefix = "";
	Throwable exception;

	WarningLevel directLevel = WarningLevel.NONE;
	WarningLevel cachedLevel = WarningLevel.NONE;

	PluginGuiIcon mainIcon = GuiManagerImpl.ICON_NULL;
	PluginGuiIcon subIcon = null;

	final List<TempQuilt2OldStatusNode> childrenByAddition, childrenByAlphabetical;

	public TempQuilt2OldStatusNode(TempQuilt2OldStatusNode parent) {
		this.parent = parent;
		childrenByAddition = new ArrayList<>();
		childrenByAlphabetical = new ArrayList<>();
	}

	public void toNode(QuiltStatusTree.QuiltStatusNode node, boolean debug) {
		node.name = text;

		if (mainIcon != GuiManagerImpl.ICON_NULL) {
			node.iconType = mainIcon.tempToStatusNodeStr() + (subIcon == null ? "" : ("+" + subIcon
				.tempToStatusNodeStr()));
		} else if (subIcon != null) {
			node.iconType = subIcon.tempToStatusNodeStr();
		}

		node.setWarningLevel(FabricTreeWarningLevel.fromApiLevel(directLevel));
		node.expandByDefault = true;

		for (TempQuilt2OldStatusNode n : childrenByAddition) {
			if (debug || n.directLevel != WarningLevel.DEBUG_ONLY) {
				n.toNode(node.addChild(n.text), debug);
			}
		}

		childrenByAlphabetical.sort(Comparator.comparing((TempQuilt2OldStatusNode a) -> a.sortPrefix).thenComparing(a -> a.text));

		for (TempQuilt2OldStatusNode n : childrenByAlphabetical) {
			if (debug || n.directLevel != WarningLevel.DEBUG_ONLY) {
				n.toNode(node.addChild(n.text), debug);
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
		return addChild("", sortOrder);
	}

	@Override
	public PluginGuiTreeNode addChild(String text, SortOrder sortOrder) {
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
	public String text() {
		return text;
	}

	@Override
	public TempQuilt2OldStatusNode text(String text) {
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

		if (parent != null) {
			parent.resetCachedLevel();
		}
		return this;
	}

	private void resetCachedLevel() {
		WarningLevel max = directLevel;

		for (TempQuilt2OldStatusNode c : childrenByAddition) {
			if (max.ordinal() < c.getMaximumLevel().ordinal()) {
				max = c.getMaximumLevel();
			}
		}

		for (TempQuilt2OldStatusNode c : childrenByAlphabetical) {
			if (max.ordinal() < c.getMaximumLevel().ordinal()) {
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
}
