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

	@Override
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
