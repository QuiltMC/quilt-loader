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

package org.quiltmc.loader.impl.plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.impl.gui.QuiltJsonGui;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonAction;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonButton;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonGuiMessage;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.plugin.gui.PluginIconImpl;

public class QuiltPluginErrorImpl implements QuiltPluginError {

	final String reportingPlugin;
	final Text title;
	PluginGuiIcon icon = GuiManagerImpl.ICON_LEVEL_ERROR;
	int ordering = 0;
	final List<String> reportLines = new ArrayList<>();
	final List<Text> description = new ArrayList<>();
	final List<Text> additionalInfo = new ArrayList<>();
	final List<Throwable> exceptions = new ArrayList<>();
	final List<ErrorButton> buttons = new ArrayList<>();
	final Throwable reportTrace;

	public QuiltPluginErrorImpl(String reportingPlugin, Text title) {
		this.reportingPlugin = reportingPlugin;
		this.title = title;
		this.reportTrace = new Throwable();
	}

	@Override
	public QuiltPluginError appendReportText(String... lines) {
		Collections.addAll(reportLines, lines);
		return this;
	}

	@Override
	public QuiltPluginError setOrdering(int priority) {
		this.ordering = priority;
		return this;
	}

	@Override
	public QuiltPluginError appendDescription(Text... descriptions) {
		Collections.addAll(description, descriptions);
		return this;
	}

	@Override
	public QuiltPluginError appendAdditionalInformation(Text... information) {
		Collections.addAll(additionalInfo, information);
		return this;
	}

	@Override
	public QuiltPluginError appendThrowable(Throwable t) {
		exceptions.add(t);
		return this;
	}

	@Override
	public QuiltPluginError setIcon(PluginGuiIcon icon) {
		this.icon = icon;
		return this;
	}

	private ErrorButton button(Text name, QuiltBasicButtonAction action) {
		ErrorButton button = new ErrorButton(name, action);
		buttons.add(button);
		return button;
	}

	@Override
	public QuiltPluginButton addFileViewButton(Text name, Path openedPath) {
		return button(name, QuiltBasicButtonAction.VIEW_FILE).arg("file", openedPath.toString());
	}

	@Override
	public QuiltPluginButton addFolderViewButton(Text name, Path openedFolder) {
		if (Files.exists(openedFolder) && Files.isRegularFile(openedFolder)) {
			return addFileViewButton(name, openedFolder);
		} else {
			return button(name, QuiltBasicButtonAction.VIEW_FOLDER).arg("folder", openedFolder.toString());
		}
	}

	@Override
	public QuiltPluginButton addOpenLinkButton(Text name, String url) {
		return button(name, QuiltBasicButtonAction.OPEN_WEB_URL).arg("url", url);
	}

	@Override
	public QuiltPluginButton addCopyTextToClipboardButton(Text name, String fullText) {
		return button(name, QuiltBasicButtonAction.PASTE_CLIPBOARD_TEXT).arg("text", fullText);
	}

	@Override
	public QuiltPluginButton addCopyFileToClipboardButton(Text name, Path openedFile) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	public List<String> toReportText() {
		List<String> lines = new ArrayList<>();
		lines.addAll(reportLines);

		if (lines.isEmpty()) {
			lines.add("The plugin that created this error (" + reportingPlugin + ") forgot to call 'appendReportText'!");
			lines.add("The next stacktrace is where the plugin created the error, not the actual error.'");
			exceptions.add(0, reportTrace);
		}

		for (Throwable ex : exceptions) {
			lines.add("");
			StringWriter writer = new StringWriter();
			ex.printStackTrace(new PrintWriter(writer));
			Collections.addAll(lines, writer.toString().split("\n"));
		}
		return lines;
	}

	public QuiltJsonGuiMessage toGuiMessage(QuiltJsonGui json) {
		QuiltJsonGuiMessage msg = new QuiltJsonGuiMessage();

		// TODO: Change the gui json stuff to embed 'Text' rather than 'String'
		msg.title = title.toString();
		msg.iconType = PluginIconImpl.fromApi(icon).path;
		for (Text t : description) {
			for (String line : t.toString().split("\\n")) {
				msg.description.add(line);
			}
		}

		for (Text t : additionalInfo) {
			for (String line : t.toString().split("\\n")) {
				msg.additionalInfo.add(line);
			}
		}

		StringBuilder reportText = new StringBuilder();
		for (String line : toReportText()) {
			reportText.append(line);
			reportText.append("\n");
		}
		addCopyTextToClipboardButton(Text.translate("button.copy_section"), reportText.toString());

		for (ErrorButton btn : buttons) {
			msg.buttons.add(btn.toGuiButton(json));
		}

		return msg;
	}

	static class ErrorButton implements QuiltPluginButton {
		final Text name;
		final QuiltBasicButtonAction action;
		PluginGuiIcon icon;
		final Map<String, String> arguments = new HashMap<>();

		public ErrorButton(Text name, QuiltBasicButtonAction action) {
			this.name = name;
			this.action = action;
		}

		protected QuiltJsonButton toGuiButton(QuiltJsonGui json) {
			String iconStr = icon == null ? action.defaultIcon : PluginIconImpl.fromApi(icon).path;
			return new QuiltJsonButton(name.toString(), iconStr, action).arguments(arguments);
		}

		@Override
		public ErrorButton icon(PluginGuiIcon icon) {
			this.icon = icon;
			return this;
		}

		ErrorButton arg(String key, String value) {
			arguments.put(key, value);
			return this;
		}
	}
}
