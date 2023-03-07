package org.quiltmc.loader.impl.gui;

import java.io.IOException;
import java.util.Map;

import org.quiltmc.json5.JsonWriter;
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
		node = new QuiltStatusNode(null, HELPER.expectObject(obj, "node"));
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("level", lvf().string(filterLevel.lowerCaseName));
		// map.put("node", node)
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
