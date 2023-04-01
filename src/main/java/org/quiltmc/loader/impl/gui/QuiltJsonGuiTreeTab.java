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
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
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
