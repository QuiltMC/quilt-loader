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

package net.fabricmc.loader.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.metadata.ModOriginImpl;
import net.fabricmc.loader.metadata.LoaderModMetadata;

@Deprecated
public final class ModContainerImpl extends net.fabricmc.loader.ModContainer {
	private final ModContainer quilt;

	public ModContainerImpl(ModContainer quilt) {
		this.quilt = quilt;
	}

	@Override
	public ModMetadata getMetadata() {
		return getInfo();
	}

	@Override
	public LoaderModMetadata getInfo() {
		return ((ConvertibleModMetadata) quilt.metadata()).asFabricModMetadata();
	}

	@Override
	public List<Path> getRootPaths() {
		return Collections.singletonList(getRootPath());
	}

	@Override
	public ModOrigin getOrigin() {
		return new ModOriginImpl(quilt);
	}

	@Override
	public URL getOriginUrl() {
		try {
			return quilt.getSourcePaths().get(0).get(0).toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Optional<net.fabricmc.loader.api.ModContainer> getContainingMod() {
		for (List<Path> paths : quilt.getSourcePaths()) {
			if (paths.size() < 2) {
				continue;
			}
			List<Path> subtracted = new ArrayList<>(paths);
			subtracted.remove(subtracted.size() - 1);
			for (ModContainer container : QuiltLoader.getAllMods()) {
				if (container.getSourcePaths().contains(subtracted)) {
					return Optional.of(new ModContainerImpl(quilt));
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Collection<net.fabricmc.loader.api.ModContainer> getContainedMods() {
		List<net.fabricmc.loader.api.ModContainer> contained = new ArrayList<>();
		for (ModContainer other : QuiltLoader.getAllMods()) {
			if (other == quilt) {
				continue;
			}
			for (List<Path> paths : other.getSourcePaths()) {
				if (paths.size() < 2) {
					continue;
				}
				List<Path> subtracted = new ArrayList<>(paths);
				subtracted.remove(subtracted.size() - 1);
				if (quilt.getSourcePaths().contains(subtracted)) {
					contained.add(new ModContainerImpl(other));
					break;
				}
			}
		}
		return contained;
	}

	public ModContainer getQuiltModContainer() {
		return quilt;
	}

	// copy + pasted from fabric
	private boolean warnedMultiPath;

	@Override
	public Path getRootPath() {
		return quilt.rootPath();
	}

	@Override
	public Path getPath(String file) {
		Optional<Path> res = findPath(file);
		if (res.isPresent()) return res.get();

		List<Path> roots = this.getRootPaths();

		if (!roots.isEmpty()) {
			Path root = roots.get(0);

			return root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
		} else {
			return Paths.get(".").resolve("missing_ae236f4970ce").resolve(file.replace('/', File.separatorChar)); // missing dummy path
		}
	}
}
