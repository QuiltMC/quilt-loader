package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;

public final class QuiltJsonGuiTreeTab extends QuiltGuiSyncBase {
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
		node = readChild(HELPER.expectValue(obj, "node"), QuiltStatusNode.class);
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("level", lvf().string(filterLevel.lowerCaseName));
		map.put("node", writeChild(node));
	}

	@Override
	String syncType() {
		return "tree_tab";
	}
}
