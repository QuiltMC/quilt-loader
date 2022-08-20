package org.quiltmc.loader.impl.plugin.gui;

import org.quiltmc.loader.api.plugin.gui.Text;

import java.util.MissingFormatArgumentException;

public final class TextImpl implements Text {

	private final String translationKey;
	private final Object[] extra;
	boolean translate;

	public TextImpl(String key, boolean translate, Object... args) {
		this.translationKey = key;
		this.extra = args;
		this.translate = true;
	}

	public String toString() {
		try {
			return String.format(translate ? I18n.translate(translationKey) : translationKey, extra);
		} catch (MissingFormatArgumentException e) {
			StringBuilder sb = new StringBuilder(translationKey);
			for (Object o : extra) {
				sb.append(' ').append(o);
			}

			return sb.toString();
		}

	}
}
