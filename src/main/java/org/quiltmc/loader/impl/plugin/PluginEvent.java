package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;

/** We don't have a lot of events so instead of using a proper event system we just keep track of them internally. */
abstract class PluginEvent {

	static final class AddFolderEvent extends PluginEvent {
		final Path added;

		AddFolderEvent(Path added) {
			this.added = added;
		}
	}
}
