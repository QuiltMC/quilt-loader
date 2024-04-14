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

public enum QuiltWarningLevel {

	/** A serious error that forces loading to halt immediately, and display the current state of loading.
	 * <p>
	 * This should only be used if there's a bug in either quilt loader, a plugin, or a mod, which needs a developer
	 * to fix. */
	FATAL(QuiltLoaderGui.iconLevelFatal()),

	/** This indicates that something has gone wrong which prevents the game from loading, but is either potentially
	 * recoverable or not serious enough to halt loading immediately. This indicates a critical error occurred that the
	 * user needs to fix manually, or report to quilt-loader if it's an internal bug, in order to run the game. */
	ERROR(QuiltLoaderGui.iconLevelFatal()),

	/** Something that the user might want to know about. This (by default) forces the "files" window to open, which
	 * will display this warning. */
	WARN(QuiltLoaderGui.iconLevelWarn()),

	/** Something odd that someone might want to look at, but it isn't necessarily a problem in any context. For
	 * example 'this folder doesn't match the current game version'. */
	INFO(QuiltLoaderGui.iconLevelInfo()),

	/** A 'lesser' warning that a mod developer might want to know about. Use this when you don't want to inform the
	 * user that something is wrong. */
	CONCERN(QuiltLoaderGui.iconLevelConcern()),

	/** The default {@link QuiltWarningLevel}, which indicates nothing special about the node - however the user might
	 * still want to know about it, hence why it's still included in the gui. */
	NONE(null),

	/** A special {@link QuiltWarningLevel} which indicates that this node will be hidden if the debug option is turned
	 * off. Use this for very-fine details which are only useful during plugin development, or debugging plugins
	 * specifically. */
	DEBUG_ONLY(null);

	private final QuiltLoaderIcon icon;

	private QuiltWarningLevel(QuiltLoaderIcon icon) {
		this.icon = icon;
	}

	public static QuiltWarningLevel getHighest(QuiltWarningLevel a, QuiltWarningLevel b) {
		return values()[Math.min(a.ordinal(), b.ordinal())];
	}

	public QuiltLoaderIcon icon() {
		return icon;
	}
}
