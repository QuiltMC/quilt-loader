package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.impl.gui.QuiltJsonGui;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltBasicButtonType;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonButton;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonGuiMessage;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.plugin.gui.TextImpl;

public class QuiltPluginErrorImpl implements QuiltPluginError {

	final String reportingPlugin;
	final Text title;
	PluginGuiIcon icon = GuiManagerImpl.ICON_LEVEL_ERROR;
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

	@Override
	public QuiltPluginError addFileViewButton(Text name, Path openedPath) {
		buttons.add(new FileViewButton(name, openedPath));
		return this;
	}

	@Override
	public QuiltPluginError addOpenLinkButton(Text name, String url) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	public QuiltJsonGuiMessage toGuiMessage(QuiltJsonGui json) {
		QuiltJsonGuiMessage msg = new QuiltJsonGuiMessage();

		// TODO: Change the gui json stuff to embed 'Text' rather than 'String'
		msg.title = title.toString();
		for (Text t : description) {
			msg.description.add(t.toString());
		}
		for (Text t : additionalInfo) {
			msg.additionalInfo.add(t.toString());
		}

		for (ErrorButton btn : buttons) {
			msg.buttons.add(btn.toGuiButton(json));
		}

		return msg;
	}

	static abstract class ErrorButton {
		final Text name;

		public ErrorButton(Text name) {
			this.name = name;
		}

		protected abstract QuiltJsonButton toGuiButton(QuiltJsonGui json);
	}

	static class FileViewButton extends ErrorButton {
		final Path file;

		public FileViewButton(Text name, Path file) {
			super(name);
			this.file = file;
		}

		@Override
		protected QuiltJsonButton toGuiButton(QuiltJsonGui json) {
			// TODO: Change json gui buttons to actually work!
			return new QuiltJsonButton(Text.translate("button.view_file").toString(), QuiltBasicButtonType.CLICK_MANY);
		}
	}
}
