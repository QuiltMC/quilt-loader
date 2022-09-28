/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;

/** A task that must be completed by the main quilt thread. */
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
		final boolean fromClasspath;
		final PluginGuiTreeNode guiNode;

		public ScanZipTask(Path zipFile, Path zipRoot, boolean fromClasspath, PluginGuiTreeNode guiNode) {
			this.zipFile = zipFile;
			this.zipRoot = zipRoot;
			this.fromClasspath = fromClasspath;
			this.guiNode = guiNode;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanZip(zipFile, zipRoot, fromClasspath, guiNode);
		}
	}

	static final class ScanUnknownFileTask extends MainThreadTask {
		final Path file;
		final boolean fromClasspath;
		final PluginGuiTreeNode guiNode;

		public ScanUnknownFileTask(Path file, boolean fromClasspath, PluginGuiTreeNode guiNode) {
			this.file = file;
			this.fromClasspath = fromClasspath;
			this.guiNode = guiNode;
		}

		@Override
		void execute(QuiltPluginManagerImpl manager) {
			manager.scanUnknownFile(file, fromClasspath, guiNode);
		}
	}
}
