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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.SortedMap;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/**
 * Implementation of an icon lookup.
 */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public interface Icons {
	/**
	 * @see org.quiltmc.loader.api.ModMetadata#icon(int)
	 */
	@Nullable
	String getIcon(int size);

	/**
	 * Implementation for a mod.
	 */
	@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
	public final class Single implements Icons {
		@Nullable
		private final String icon;

		public Single(@Nullable String icon) {
			this.icon = icon;
		}

		@Nullable
		@Override
		public String getIcon(int size) {
			return this.icon;
		}
	}

	/**
	 * Implementation for a mod which has multiple icons of different sizes.
	 */
	@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
	public final class Multiple implements Icons {
		private final SortedMap<Integer, String> icons;

		public Multiple(SortedMap<Integer, String> icons) {
			this.icons = icons;
		}

		@Nullable
		@Override
		public String getIcon(int size) {
			int iconValue = -1;

			for (int entrySize : this.icons.keySet()) {
				iconValue = entrySize;

				if (iconValue >= size) {
					break;
				}
			}
			return this.icons.get(iconValue);
		}
	}
}
