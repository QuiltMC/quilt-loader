package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;

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

		public ScanZipTask(Path zipFile, Path zipRoot) {
			this.zipFile = zipFile;
			this.zipRoot = zipRoot;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanZip(zipFile, zipRoot);
		}
	}

	static final class ScanUnknownFileTask extends MainThreadTask {
		final Path file;

		public ScanUnknownFileTask(Path file) {
			this.file = file;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanUnknownFile(file);
		}
	}
}
