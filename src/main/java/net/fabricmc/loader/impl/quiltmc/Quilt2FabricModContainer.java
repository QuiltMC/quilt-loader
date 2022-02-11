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

package net.fabricmc.loader.impl.quiltmc;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.metadata.ModOrigin;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;

import net.fabricmc.loader.api.metadata.ModMetadata;

import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

public final class Quilt2FabricModContainer implements net.fabricmc.loader.api.ModContainer {
	private final ModContainer quilt;

	public Quilt2FabricModContainer(ModContainer quilt) {
		this.quilt = quilt;
	}

	@Override
	public ModMetadata getMetadata() {
		return ((ConvertibleModMetadata) quilt.metadata()).asFabricModMetadata();
	}

	@Override
	public List<Path> getRootPaths() {
		return Collections.singletonList(getRootPath());
	}

	@Override
	public ModOrigin getOrigin() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Optional<net.fabricmc.loader.api.ModContainer> getContainingMod() {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Collection<net.fabricmc.loader.api.ModContainer> getContainedMods() {
		throw new UnsupportedOperationException("not implemented");
	}

	// copy + pasted from fabric
	private boolean warnedMultiPath;
	@Override
	public Path getRootPath() {
		List<Path> paths = getRootPaths();
		if (paths.size() != 1 && !warnedMultiPath) {
			if (!QuiltLoaderImpl.INSTANCE.isDevelopmentEnvironment()) warnedMultiPath = true;
			Log.warn(LogCategory.GENERAL, "getRootPath access for %s with multiple paths, returning only one which may incur unexpected behavior!", this);
		}

		return paths.get(0);
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
