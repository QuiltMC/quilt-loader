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

import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class PluginIconImpl implements PluginGuiIcon {
	public final String path;

	PluginIconImpl(String path) {
		this.path = path;
	}

	PluginIconImpl(int index) {
		this.path = "!" + index;
	}

	public static PluginIconImpl fromApi(PluginGuiIcon icon) {
		if (icon instanceof PluginIconImpl) {
			return (PluginIconImpl) icon;
		} else if (icon == null) {
			return null;
		} else {
			throw new IllegalArgumentException(icon.getClass() + " implements PluginGuiIcon, even though this is disallowed!");
		}
	}

	@Override
	public PluginIconImpl withDecoration(PluginGuiIcon subIcon) {
		if (subIcon == null) {
			return this;
		}
		return new PluginIconImpl(path + "+" + fromApi(subIcon).path);
	}
}
