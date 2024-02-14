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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltBasicWindow;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.api.gui.QuiltGuiMessagesTab;
import org.quiltmc.loader.api.gui.QuiltGuiTreeTab;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.impl.gui.QuiltJsonButton.QuiltBasicButtonAction;

class BasicWindow<R> extends AbstractWindow<R> implements QuiltBasicWindow<R>, ButtonContainerImpl {

	interface BasicWindowChangeListener extends WindowChangeListener, ButtonContainerListener {
		default void onMainTextChanged() {}
		default void onAddTab(AbstractTab tab) {}
	}

	String mainText = "";
	boolean singleTabOnly = false;
	private QuiltLoaderText apiMainText = QuiltLoaderText.EMPTY;

	final List<QuiltJsonButton> buttons = new ArrayList<>();
	final List<AbstractTab> tabs = new ArrayList<>();

	BasicWindow(R defaultReturnValue) {
		super(defaultReturnValue);
	}

	BasicWindow(QuiltGuiSyncBase parent, LObject obj) throws IOException {
		super(parent, obj);
		this.mainText = HELPER.expectString(obj, "mainText");
		this.singleTabOnly = HELPER.expectBoolean(obj, "singleTabOnly");
		for (LoaderValue sub : HELPER.expectArray(obj, "buttons")) {
			buttons.add(readChild(sub, QuiltJsonButton.class));
		}
		for (LoaderValue sub : HELPER.expectArray(obj, "tabs")) {
			tabs.add(readChild(sub, AbstractTab.class));
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		super.write0(map);
		map.put("mainText", lvf().string(mainText));
		map.put("singleTabOnly", lvf().bool(singleTabOnly));
		map.put("buttons", lvf().array(write(buttons)));
		for (AbstractTab tab : tabs) {
			// This ensures we don't need to construct it from an unknown class
			tab.send();
		}
		map.put("tabs", lvf().array(write(tabs)));
	}

	@Override
	String syncType() {
		return "basic_window";
	}

	@Override
	protected void openOnServer() {
		try {
			BasicWindowUI.open(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public BasicWindow<R> getThis() {
		return this;
	}

	@Override
	public QuiltJsonButton addButton(QuiltJsonButton button) {
		assert button.parent == this;
		buttons.add(button);
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("button", writeChild(button));
			sendUpdate("add_button", lvf().object(map));
		}
		return button;
	}

	@Override
	public QuiltErrorButton addContinueButton() {
		return button(QuiltLoaderText.translate("button.continue"), QuiltBasicButtonAction.CONTINUE);
	}

	@Override
	public QuiltLoaderText mainText() {
		return apiMainText;
	}

	@Override
	public void mainText(QuiltLoaderText text) {
		this.apiMainText = Objects.requireNonNull(text);
		mainText = apiMainText.toString();
		if (shouldSendUpdates()) {
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("text", lvf().string(this.title));
			sendUpdate("set_main_text", lvf().object(map));
		}
	}

	@Override
	public void restrictToSingleTab() {
		if (tabs.size() != 1) {
			throw new IllegalStateException("Wrong number of tabs! " + tabs.size());
		}
		if (shouldSendUpdates()) {
			throw new IllegalStateException("Already opened!");
		}
		singleTabOnly = true;
	}

	@Override
	public QuiltGuiMessagesTab addMessagesTab(QuiltLoaderText name) {
		if (singleTabOnly && tabs.size() < 2) {
			throw new IllegalStateException("Cannot add tabs when we're in single tab now.");
		}
		return addTab(new MessagesTab(this, name));
	}

	@Override
	public QuiltGuiTreeTab addTreeTab(QuiltLoaderText name) {
		if (singleTabOnly && tabs.size() < 2) {
			throw new IllegalStateException("Cannot add tabs when we're in single tab now.");
		}
		return addTab(new TreeTab(this, name));
	}

	@Override
	public QuiltGuiTreeTab addTreeTab(QuiltLoaderText name, QuiltTreeNode rootNode) {
		if (singleTabOnly && tabs.size() < 2) {
			throw new IllegalStateException("Cannot add tabs when we're in single tab now.");
		}
		return addTab(new TreeTab(this, name, (QuiltStatusNode) rootNode));
	}

	protected <T extends AbstractTab> T addTab(T tab) {
		tabs.add(tab);
		if (shouldSendUpdates()) {
			tab.send();
			Map<String, LoaderValue> map = new HashMap<>();
			map.put("tab", writeChild(tab));
			sendUpdate("add_tab", lvf().object(map));
		}
		return tab;
	}

	@Override
	void handleUpdate(String name, LObject data) throws IOException {
		switch (name) {
			case "set_main_text": {
				this.mainText = HELPER.expectString(data, "text");
				invokeListeners(BasicWindowChangeListener.class, BasicWindowChangeListener::onMainTextChanged);
				break;
			}
			case "add_button": {
				LoaderValue value = HELPER.expectValue(data, "button");
				QuiltJsonButton button = readChild(value, QuiltJsonButton.class);
				buttons.add(button);
				invokeListeners(ButtonContainerListener.class, l -> l.onButtonAdded(button));
				break;
			}
			case "add_tab": {
				LoaderValue value = HELPER.expectValue(data, "tab");
				AbstractTab tab = readChild(value, AbstractTab.class);
				tabs.add(tab);
				invokeListeners(BasicWindowChangeListener.class, l -> l.onAddTab(tab));
				break;
			}
			default: {
				super.handleUpdate(name, data);
			}
		}
	}
}
