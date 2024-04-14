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
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltGuiTab;
import org.quiltmc.loader.api.gui.QuiltGuiTreeTab;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;

class TreeTab extends AbstractTab implements QuiltGuiTreeTab {

	final QuiltStatusNode rootNode;
	boolean inheritLevel = true;
	QuiltWarningLevel visibilityLevel = QuiltWarningLevel.NONE;

	TreeTab(BasicWindow<?> parent, QuiltLoaderText text) {
		super(parent, text);
		rootNode = new QuiltStatusNode(this);
	}

	TreeTab(BasicWindow<?> parent, QuiltLoaderText text, QuiltStatusNode rootNode) {
		super(parent, text);
		if (rootNode.parent != null) {
			throw new IllegalArgumentException("Cannot use a different root node if the other root node already has a parent!");
		}
		this.rootNode = rootNode;
	}

	TreeTab(QuiltGuiSyncBase parent, LObject obj) throws IOException {
		super(parent, obj);
		rootNode = readChild(HELPER.expectValue(obj, "root_node"), QuiltStatusNode.class);
		inheritLevel = HELPER.expectBoolean(obj, "inherit_level");
		visibilityLevel = HELPER.expectEnum(QuiltWarningLevel.class, obj, "visibilityLevel");
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		super.write0(map);
		map.put("root_node", writeChild(rootNode));
		map.put("inherit_level", lvf().bool(inheritLevel));
		map.put("visibilityLevel", lvf().string(visibilityLevel.name()));
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "set_inherit_level": {
				this.inheritLevel = HELPER.expectBoolean(data, "inherit_level");
				invokeListeners(TabChangeListener.class, TabChangeListener::onLevelChanged);
				break;
			}
			default: {
				super.handleUpdate(name, data);
			}
		}
	}

	@Override
	QuiltWarningLevel getInheritedLevel() {
		return rootNode.maximumLevel();
	}

	@Override
	String syncType() {
		return "tree_tab";
	}

	@Override
	public QuiltTreeNode rootNode() {
		return rootNode;
	}

	@Override
	public QuiltWarningLevel level() {
		if (inheritLevel) {
			return rootNode.maximumLevel();
		} else {
			return super.level();
		}
	}

	@Override
	public QuiltGuiTab level(QuiltWarningLevel level) {
		inheritLevel(false);
		return super.level(level);
	}

	@Override
	public QuiltGuiTreeTab inheritLevel(boolean should) {
		this.inheritLevel = should;
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("inherit_level", lvf().bool(should));
			sendUpdate("set_inherit_level", lvf().object(map));
		}
		return this;
	}


	@Override
	public QuiltGuiTreeTab visibilityLevel(QuiltWarningLevel level) {
		visibilityLevel = level;
		return this;
	}
}
