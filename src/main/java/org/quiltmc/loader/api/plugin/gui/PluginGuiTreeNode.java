package org.quiltmc.loader.api.plugin.gui;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.gui.QuiltStatusTree.FabricTreeWarningLevel;

public interface PluginGuiTreeNode {

	public enum WarningLevel {

		/** A serious error that forces plugin loading to halt immediately, and display the current state of loading.
		 * <p>
		 * This should only be used if there's a bug in either quilt loader or a plugin, which needs a developer to
		 * fix. */
		FATAL,

		/** If any {@link PluginGuiTreeNode} have this level then <em>loading will fail</em>. a critical error occurred
		 * that the user needs to fix manually, or report to quilt-loader if it's an internal bug. */
		ERROR,

		/** Something that the user might want to know about. This (by default) forces the "files" window to open, which
		 * will display this warning. */
		WARN,

		/** A 'lesser' warning that a mod developer might want to know about. Use this when you don't want to inform the
		 * user that something is wrong. */
		CONCERN,

		/** Something odd that someone might want to look at, but it isn't necessarily a problem in any context. I.E.
		 * 'this folder doesn't match the current game version'. */
		INFO,

		/** The default {@link WarningLevel}, which indicates nothing special about the node - however the user might
		 * still want to know about it, hence why it's still included in the gui. */
		NONE,

		/** A special {@link WarningLevel} which indicates that this node will be hidden if the debug option is turned
		 * off. Use this for very-fine details which are only useful during plugin development, or debugging plugins
		 * specifically. */
		DEBUG_ONLY;

		public FabricTreeWarningLevel toQuiltLevel() {
			switch (this) {
				case FATAL: {
					return FabricTreeWarningLevel.FATAL;
				}
				case ERROR: {
					return FabricTreeWarningLevel.ERROR;
				}
				case WARN: {
					return FabricTreeWarningLevel.WARN;
				}
				case CONCERN: {
					return FabricTreeWarningLevel.CONCERN;
				}
				case INFO: {
					return FabricTreeWarningLevel.INFO;
				}
				case NONE:
				default:
					return FabricTreeWarningLevel.NONE;
			}
		}
	}

	public enum SortOrder {
		ADDITION_ORDER,
		ALPHABETICAL_ORDER,
	}

	PluginGuiManager manager();

	@Nullable
	PluginGuiTreeNode parent();

	PluginGuiTreeNode addChild(SortOrder sortOrder);

	PluginGuiTreeNode addChild(String text, SortOrder sortOrder);

	default PluginGuiTreeNode addChild(String text) {
		return addChild(text, SortOrder.ADDITION_ORDER);
	}

	String text();

	PluginGuiTreeNode text(String text);

	String sortPrefix();

	PluginGuiTreeNode sortPrefix(String prefix);

	/** Sets the {@link WarningLevel} of this node. If it's {@link WarningLevel#ERROR} then <em>loading will fail</em>.
	 * Use this when a critical error occurred that the user needs to fix manually. */
	PluginGuiTreeNode setDirectLevel(WarningLevel level);

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

	default PluginGuiTreeNode setError(Throwable exception) {
		setDirectLevel(WarningLevel.ERROR);
		setException(exception);
		return this;
	}

	default PluginGuiTreeNode setWarn(Throwable exception) {
		setDirectLevel(WarningLevel.WARN);
		setException(exception);
		return this;
	}

	PluginGuiTreeNode setException(Throwable exception);

	WarningLevel getDirectLevel();

	/** @return The maximum level of all children and this node. */
	WarningLevel getMaximumLevel();

	PluginGuiIcon mainIcon();

	PluginGuiTreeNode mainIcon(PluginGuiIcon icon);

	@Nullable
	PluginGuiIcon subIcon();

	PluginGuiTreeNode subIcon(PluginGuiIcon icon);
}