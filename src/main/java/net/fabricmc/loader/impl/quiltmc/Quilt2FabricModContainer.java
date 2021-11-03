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

import java.nio.file.Path;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;

import net.fabricmc.loader.api.metadata.ModMetadata;

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
	public Path getRootPath() {
		return quilt.rootPath();
	}
}
