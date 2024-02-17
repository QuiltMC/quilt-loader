/*
 * Copyright 2022, 2023 QuiltMC
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class PluginIconImpl extends QuiltGuiSyncBase implements QuiltLoaderIcon {

	static final int BOTTOM_LEFT = QuiltLoaderIcon.SubIconPosition.BOTTOM_LEFT.ordinal();
	static final int BOTTOM_RIGHT = QuiltLoaderIcon.SubIconPosition.BOTTOM_RIGHT.ordinal();
	static final int TOP_RIGHT = QuiltLoaderIcon.SubIconPosition.TOP_RIGHT.ordinal();
	static final int TOP_LEFT = QuiltLoaderIcon.SubIconPosition.TOP_LEFT.ordinal();

	static final int[] LEVEL_ORDER = { BOTTOM_LEFT, BOTTOM_RIGHT, TOP_RIGHT, TOP_LEFT };
	static final int[] REGULAR_ORDER = { BOTTOM_RIGHT, TOP_RIGHT, TOP_LEFT, BOTTOM_LEFT };

	final IconType icon;
	final IconType[] subIcons = new IconType[4];

	PluginIconImpl() {
		super(null);
		this.icon = new BlankIcon();
	}

	PluginIconImpl(String path) {
		super(null);
		this.icon = new BuiltinIcon(path);
	}

	@Deprecated
	PluginIconImpl(int index) {
		super(null);
		this.icon = new LegacyUploadedIcon(index);
	}

	/** @param images Array of images of different sizes, to be chosen by the UI. */
	PluginIconImpl(byte[][] images) {
		super(null);
		this.icon = new UploadedIcon(images);
	}

	private PluginIconImpl(IconType icon, IconType[] subIcons) {
		super(null);
		this.icon = icon;
		System.arraycopy(subIcons, 0, this.subIcons, 0, 4);
	}

	public PluginIconImpl(QuiltGuiSyncBase parent, LObject obj) throws IOException {
		super(parent, obj);
		this.icon = readChild(HELPER.expectValue(obj, "icon"), IconType.class);
		for (int i = 0; i < 4; i++) {
			String key = "subIcon[" + i + "]";
			if (obj.containsKey(key)) {
				subIcons[i] = readChild(HELPER.expectValue(obj, key), IconType.class);
			}
		}
	}

	@Override
	protected void write0(Map<String, LoaderValue> map) {
		map.put("icon", writeChild(icon));
		for (int i = 0; i < 4; i++) {
			if (subIcons[i] != null) {
				map.put("subIcon[" + i + "]", writeChild(subIcons[i]));
			}
		}
	}

	@Override
	String syncType() {
		return "icon";
	}

	@Deprecated
	public static QuiltLoaderIcon deprecatedForFabric(String path) {
		return new PluginIconImpl(path);
	}

	public static PluginIconImpl fromApi(QuiltLoaderIcon icon) {
		if (icon instanceof PluginIconImpl) {
			return (PluginIconImpl) icon;
		} else if (icon == null) {
			return null;
		} else {
			throw new IllegalArgumentException(icon.getClass() + " implements QuiltLoaderIcon, even though this is disallowed!");
		}
	}

	@Override
	public QuiltLoaderIcon getDecoration(SubIconPosition position) {
		return new PluginIconImpl(subIcons[position.ordinal()], new IconType[4]);
	}

	@Override
	public PluginIconImpl withDecoration(SubIconPosition position, QuiltLoaderIcon subIcon) {
		PluginIconImpl impl = fromApi(subIcon);

		if (impl == null) {
			if (subIcons[position.ordinal()] == null) {
				return this;
			}
		} else {
			for (IconType subSub : impl.subIcons) {
				if (subSub != null) {
					throw new IllegalArgumentException("The given icon aready has sub-icons!");
				}
			}
		}

		IconType[] newArray = Arrays.copyOf(subIcons, subIcons.length);
		newArray[position.ordinal()] = impl == null ? null : impl.icon;
		return new PluginIconImpl(this.icon, newArray);
	}

	@Override
	public PluginIconImpl withDecoration(QuiltLoaderIcon subIcon) {
		return withSub(REGULAR_ORDER, subIcon);
	}

	@Override
	public PluginIconImpl withLevel(QuiltWarningLevel level) {
		return withSub(LEVEL_ORDER, level.icon());
	}

	private PluginIconImpl withSub(int[] order, QuiltLoaderIcon icon) {
		PluginIconImpl impl = fromApi(icon);
		if (impl == null) {
			return this;
		}

		for (IconType subSub : impl.subIcons) {
			if (subSub != null) {
				throw new IllegalArgumentException("The given icon aready has sub-icons!");
			}
		}

		int index = order[0];
		for (int i : order) {
			if (subIcons[i] == null) {
				index = i;
				break;
			}
		}

		IconType[] newArray = Arrays.copyOf(subIcons, subIcons.length);
		newArray[index] = impl.icon;
		return new PluginIconImpl(this.icon, newArray);
	}

	public PluginIconImpl withoutBlank() {
		if (!(icon instanceof BlankIcon)) {
			return this;
		}
		IconType main = null;
		IconType[] sub = Arrays.copyOf(subIcons, subIcons.length);
		if (sub[BOTTOM_RIGHT] != null) {
			main = sub[BOTTOM_RIGHT];
			sub[BOTTOM_RIGHT] = null;
		} else if (sub[BOTTOM_LEFT] != null) {
			main = sub[BOTTOM_LEFT];
			sub[BOTTOM_LEFT] = null;
		} else if (sub[TOP_RIGHT] != null) {
			main = sub[TOP_RIGHT];
			sub[TOP_RIGHT] = null;
		} else if (sub[TOP_LEFT] != null) {
			main = sub[TOP_LEFT];
			sub[TOP_LEFT] = null;
		} else {
			return this;
		}

		return new PluginIconImpl(main, sub);
	}

	@Override
	public String toString() {
		return "PluginIconImpl{ " + icon + " [ " + subIcons[0] + ", " + subIcons[1] + ", " + subIcons[2] + ", " + subIcons[3] + " ] }";
	}

	static abstract class IconType extends QuiltGuiSyncBase {

		private IconType() {
			super(null);
		}

		private IconType(QuiltGuiSyncBase parent, LObject obj) throws IOException {
			super(parent, obj);
		}
	}

	static final class BlankIcon extends IconType {

		public BlankIcon() {

		}

		public BlankIcon(QuiltGuiSyncBase parent, LObject obj) throws IOException {
			super(parent, obj);
		}

		@Override
		public String toString() {
			return "Blank";
		}

		@Override
		String syncType() {
			return "blank_icon";
		}

		@Override
		protected void write0(Map<String, LoaderValue> map) {
			// No data
		}
	}

	static final class LegacyUploadedIcon extends IconType {
		final int index;

		private LegacyUploadedIcon(int index) {
			this.index = index;
		}

		public LegacyUploadedIcon(QuiltGuiSyncBase parent, LObject obj) throws IOException {
			super(parent, obj);
			this.index = HELPER.expectNumber(obj, "index").intValue();
		}

		@Override
		String syncType() {
			return "icon_type_legacy";
		}

		@Override
		protected void write0(Map<String, LoaderValue> map) {
			map.put("index", lvf().number(index));
		}
	}

	static final class BuiltinIcon extends IconType {
		final String path;

		private BuiltinIcon(String path) {
			this.path = path;
		}

		public BuiltinIcon(QuiltGuiSyncBase parent, LObject obj) throws IOException {
			super(parent, obj);
			this.path = HELPER.expectString(obj, "path");
		}

		@Override
		String syncType() {
			return "icon_type_builtin";
		}

		@Override
		protected void write0(Map<String, LoaderValue> map) {
			map.put("path", lvf().string(path));
		}
	}

	static final class UploadedIcon extends IconType {

		/** Array of images of different sizes, to be chosen by the UI. */
		final byte[][] imageBytes;

		private UploadedIcon(byte[][] imageBytes) {
			this.imageBytes = imageBytes;
		}

		public UploadedIcon(QuiltGuiSyncBase parent, LObject obj) throws IOException {
			super(parent, obj);
			List<byte[]> list = new ArrayList<>();
			for (LoaderValue value : HELPER.expectArray(obj, "images")) {
				String text = HELPER.expectString(value);
				try {
					list.add(Base64.getDecoder().decode(text));
				} catch (IllegalArgumentException e) {
					throw new IOException("Bad Base64 '" + text + "'", e);
				}
			}
			imageBytes = list.toArray(new byte[0][]);
		}

		@Override
		String syncType() {
			return "icon_type_uploaded";
		}

		@Override
		protected void write0(Map<String, LoaderValue> map) {
			LoaderValue[] values = new LoaderValue[imageBytes.length];
			for (int i = 0; i < values.length; i++) {
				values[i] = lvf().string(Base64.getEncoder().encodeToString(imageBytes[i]));
			}
			map.put("images", lvf().array(values));
		}
	}
}
