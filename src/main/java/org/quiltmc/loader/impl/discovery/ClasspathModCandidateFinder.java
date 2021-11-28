/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.discovery;

import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;

public class ClasspathModCandidateFinder implements ModCandidateFinder {
	@Override
	public void findCandidates(ModCandidateFinder.ModCandidateConsumer out) {
		if (QuiltLauncherBase.getLauncher().isDevelopment()) {
			// Search for URLs which point to 'fabric.mod.json' entries, to be considered as mods.
			try {
				Enumeration<URL> mods = QuiltLauncherBase.getLauncher().getTargetClassLoader().getResources("fabric.mod.json");
				Enumeration<URL> quiltMods = QuiltLauncherBase.getLauncher().getTargetClassLoader().getResources("fabric.mod.json");
				while (quiltMods.hasMoreElements()) {
					try {
						out.accept(UrlUtil.getSourcePath("quilt.mod.json", mods.nextElement()), false);
					} catch (UrlConversionException e) {
						Log.debug(LogCategory.DISCOVERY, "Error determining location for quilt.mod.json", e);
					}
				}
				while (mods.hasMoreElements()) {
					try {
						out.accept(UrlUtil.getSourcePath("fabric.mod.json", mods.nextElement()), false);
					} catch (UrlConversionException e) {
						Log.debug(LogCategory.DISCOVERY, "Error determining location for fabric.mod.json", e);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else { // production, add loader as a mod
			try {
				out.accept(getLoaderPath(), false);
			} catch (Throwable t) {
				Log.debug(LogCategory.DISCOVERY, "Could not retrieve launcher code source!", t);
			}
		}
	}

	public static Path getLoaderPath() {
		try {
			return UrlUtil.asPath(QuiltLauncherBase.getLauncher().getClass().getProtectionDomain().getCodeSource().getLocation());
		} catch (Throwable t) {
			Log.debug(LogCategory.DISCOVERY, "Could not retrieve launcher code source!", t);
			return null;
		}
	}

	protected void addModSources(QuiltLoaderImpl loader, Set<URL> modsList, String name) throws IOException {
		Enumeration<URL> mods = QuiltLauncherBase.getLauncher().getTargetClassLoader().getResources(name);

		while (mods.hasMoreElements()) {
			try {
				modsList.add(UrlUtil.getSource(name, mods.nextElement()));
			} catch (UrlConversionException e) {
				Log.debug(LogCategory.DISCOVERY, "%s", e);
			}
		}
	}
}
