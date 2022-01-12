package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;

/** A task that must be completed by the main quilt thread (I.E. load */
abstract class MainThreadTask {

	abstract void execute(QuiltPluginManagerImpl manager);

	static final class ScanFolderTask extends MainThreadTask {
		final Path folder;
		final String pluginSrc;

		public ScanFolderTask(Path folder, String pluginSrc) {
			this.folder = folder;
			this.pluginSrc = pluginSrc;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanModFolder(folder, pluginSrc);
		}
	}

	static final class ScanZipTask extends MainThreadTask {
		final Path zipFile;
		final Path zipRoot;
		final PluginGuiTreeNode guiNode;

		public ScanZipTask(Path zipFile, Path zipRoot, PluginGuiTreeNode guiNode) {
			this.zipFile = zipFile;
			this.zipRoot = zipRoot;
			this.guiNode = guiNode;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanZip(zipFile, zipRoot, guiNode);
		}
	}

	static final class ScanUnknownFileTask extends MainThreadTask {
		final Path file;
		final PluginGuiTreeNode guiNode;

		public ScanUnknownFileTask(Path file, PluginGuiTreeNode guiNode) {
			this.file = file;
			this.guiNode = guiNode;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanUnknownFile(file, guiNode);
		}
	}
}
