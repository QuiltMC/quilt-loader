package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

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

	static abstract class ErrorButton {
		final Text name;

		public ErrorButton(Text name) {
			this.name = name;
		}
	}

	static class FileViewButton extends ErrorButton {
		final Path file;

		public FileViewButton(Text name, Path file) {
			super(name);
			this.file = file;
		}
	}
}
