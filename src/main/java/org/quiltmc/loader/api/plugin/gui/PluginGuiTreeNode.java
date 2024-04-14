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

package org.quiltmc.loader.api.plugin.gui;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** @deprecated Replaced / moved to public API: {@link QuiltTreeNode}. */

@QuiltLoaderInternal(value = QuiltLoaderInternalType.PLUGIN_API, replacements = QuiltTreeNode.class)
@Deprecated
public interface PluginGuiTreeNode {

	/** @return The newer API version of this node. */
	QuiltTreeNode getNew();

	/** @deprecated Replaced / moved to public API: {@link QuiltWarningLevel}. */
	@Deprecated
	@QuiltLoaderInternal(value = QuiltLoaderInternalType.PLUGIN_API, replacements = QuiltWarningLevel.class)
	public enum WarningLevel {

		/** A serious error that forces loading to halt immediately, and display the current state of loading.
		 * <p>
		 * This should only be used if there's a bug in either quilt loader, a plugin, or a mod, which needs a developer
		 * to fix. */
		FATAL,

		/** If any {@link PluginGuiTreeNode} have this level then <em>loading will fail</em>. This indicates a
		 * critical error occurred that the user needs to fix manually, or report to quilt-loader if it's an internal
		 * bug, in order to run the game. */
		ERROR,

		/** Something that the user might want to know about. This (by default) forces the "files" window to open, which
		 * will display this warning. */
		WARN,

		/** Something odd that someone might want to look at, but it isn't necessarily a problem in any context. For
		 * example 'this folder doesn't match the current game version'. */
		INFO,

		/** A 'lesser' warning that a mod developer might want to know about. Use this when you don't want to inform the
		 * user that something is wrong. */
		CONCERN,

		/** The default {@link WarningLevel}, which indicates nothing special about the node - however the user might
		 * still want to know about it, hence why it's still included in the gui. */
		NONE,

		/** A special {@link WarningLevel} which indicates that this node will be hidden if the debug option is turned
		 * off. Use this for very-fine details which are only useful during plugin development, or debugging plugins
		 * specifically. */
		DEBUG_ONLY;
	}

	@QuiltLoaderInternal(value = QuiltLoaderInternalType.PLUGIN_API, replacements = QuiltTreeNode.SortOrder.class)
	@Deprecated
	public enum SortOrder {
		ADDITION_ORDER,
		ALPHABETICAL_ORDER,
	}

	@Nullable
	PluginGuiTreeNode parent();

	PluginGuiTreeNode addChild(SortOrder sortOrder);

	@Contract("_, _ -> new")
	PluginGuiTreeNode addChild(QuiltLoaderText text, SortOrder sortOrder);

	@Contract("_ -> new")
	default PluginGuiTreeNode addChild(QuiltLoaderText text) {
		return addChild(text, SortOrder.ADDITION_ORDER);
	}

	QuiltLoaderText text();

	@Contract("_ -> this")
	PluginGuiTreeNode text(QuiltLoaderText text);

	String sortPrefix();

	@Contract("_ -> this")
	PluginGuiTreeNode sortPrefix(String prefix);

	/** Sets the {@link WarningLevel} of this node. If it's {@link WarningLevel#ERROR} then <em>loading will fail</em>.
	 * Use this when a critical error occurred that the user needs to fix manually. */

	@Contract("_ -> this")
	PluginGuiTreeNode setDirectLevel(WarningLevel level);

	@Contract("-> this")
	default PluginGuiTreeNode debug() {
		return setDirectLevel(WarningLevel.DEBUG_ONLY);
	}

	/** Sets the {@link WarningLevel} of this node to {@link WarningLevel#FATAL}, and re-throws the given exception.
	 * 
	 * @return Never returns - instead this method directly throws the exception.
	 * @throws T the exception passed in. */
	default <T extends Throwable> T throwFatal(T exception) throws T {
		setDirectLevel(WarningLevel.FATAL);
		setException(exception);
		throw exception;
	}

	/** Sets the {@link WarningLevel} of this node to {@link WarningLevel#ERROR}, and associates it with the given
	 * {@link QuiltDisplayedError}. */
	@Contract("_ -> this")
	default PluginGuiTreeNode setError(Throwable exception, QuiltDisplayedError reportedError) {
		setDirectLevel(WarningLevel.ERROR);
		setException(exception);
		assert reportedError != null;
		return this;
	}

	@Contract("_ -> this")
	default PluginGuiTreeNode setWarn(Throwable exception) {
		setDirectLevel(WarningLevel.WARN);
		setException(exception);
		return this;
	}

	@Contract("_ -> this")
	PluginGuiTreeNode setException(Throwable exception);

	WarningLevel getDirectLevel();

	/** @return The maximum level of all children and this node. */
	WarningLevel getMaximumLevel();

	/** @return The number of nodes (or sub-nodes) which have a {@link #getDirectLevel()} equal to the given level. */
	int countOf(WarningLevel level);

	QuiltLoaderIcon mainIcon();

	@Contract("_ -> this")
	PluginGuiTreeNode mainIcon(QuiltLoaderIcon icon);

	@Nullable
	QuiltLoaderIcon subIcon();

	@Contract("_ -> this")
	PluginGuiTreeNode subIcon(QuiltLoaderIcon icon);

	/** Whether the node is automatically expanded in the GUI. Defaults to {@code true} when {@link #getMaximumLevel()}
	 * is {@link WarningLevel#CONCERN CONCERN} or higher, and {@code false} otherwise. */
	void expandByDefault(boolean autoCollapse);

	@Deprecated
	PluginGuiManager manager();
}
