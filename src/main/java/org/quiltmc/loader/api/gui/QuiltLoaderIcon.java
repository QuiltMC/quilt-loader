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

package org.quiltmc.loader.api.gui;

import org.jetbrains.annotations.ApiStatus;

/** A displayable icon that can be shown in {@link QuiltLoaderGui} related elements. These are created from
 * {@link QuiltLoaderGui#createIcon(java.awt.image.BufferedImage)} (and related methods). */
@ApiStatus.NonExtendable
public interface QuiltLoaderIcon {

	public enum SubIconPosition {
		BOTTOM_LEFT,
		BOTTOM_RIGHT,
		TOP_RIGHT,
		TOP_LEFT;
	}

	QuiltLoaderIcon getDecoration(SubIconPosition position);

	/** Returns a new icon with a sub-icon added to this main icon. The given sub-icon must not already have sub-icons.
	 * If the given sub-icon is null then this is returned.
	 * <p>
	 * This adds icons in the following order: BOTTOM_RIGHT, TOP_RIGHT, TOP_LEFT, BOTTOM_LEFT. */
	QuiltLoaderIcon withDecoration(QuiltLoaderIcon subIcon);

	/** Returns a new icon with a sub-icon added to this main icon, if the level has a non-null
	 * {@link QuiltWarningLevel#icon()}.
	 * <p>
	 * This always adds the level to the BOTTOM_LEFT, unless there is already an icon present there and another position
	 * is free. */
	QuiltLoaderIcon withLevel(QuiltWarningLevel level);

	/** Returns a new icon with a sub-icon added to this main icon. The given sub-icon must not already have sub-icons.
	 * If the given sub-icon is null then this is returned.
	 * <p>
	 * This always adds an icon to the given position, or replaces an icon if one is already there. */
	QuiltLoaderIcon withDecoration(SubIconPosition position, QuiltLoaderIcon subIcon);
}
