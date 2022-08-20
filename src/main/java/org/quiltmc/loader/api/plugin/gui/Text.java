package org.quiltmc.loader.api.plugin.gui;

import org.quiltmc.loader.impl.plugin.gui.TextImpl;

public interface Text {
	public static final Text EMPTY = new TextImpl("", false);

	public static Text translate(String translationKey, Object... extra) {
		return new TextImpl(translationKey, true, extra);
	}

	public static Text of(String text) {
		return new TextImpl(text, false);
	}
}
