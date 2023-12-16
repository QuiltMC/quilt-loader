/*
 * Copyright 2023 QuiltMC
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

public interface QuiltGuiTreeTab extends QuiltGuiTab {

	/** @return The root node. This node isn't displayed in the gui, but all of it's children are. */
	QuiltTreeNode rootNode();

	/** @return The current {@link QuiltWarningLevel} for this tab. The default is to inherit the level directly from
	 *         the {@link #rootNode()}s {@link QuiltTreeNode#maximumLevel()} */
	@Override
	QuiltWarningLevel level();

	/** Sets the level for this tab. This also sets {@link #inheritLevel(boolean)} to false.
	 * 
	 * @param level The new {@link QuiltWarningLevel}.
	 * @return this. */
	@Override
	QuiltGuiTab level(QuiltWarningLevel level);

	/** Defaults to true.
	 * 
	 * @param should If true then this {@link #level()} will be based off the {@link #rootNode()}
	 *            {@link QuiltTreeNode#maximumLevel()}.
	 * @return this */
	QuiltGuiTreeTab inheritLevel(boolean should);

	/** Controls whether nodes are visible in the GUI. Defaults to {@link QuiltWarningLevel#NONE}, which means that
	 * {@link QuiltWarningLevel#DEBUG_ONLY} nodes aren't shown. */
	QuiltGuiTreeTab visibilityLevel(QuiltWarningLevel level);
}
