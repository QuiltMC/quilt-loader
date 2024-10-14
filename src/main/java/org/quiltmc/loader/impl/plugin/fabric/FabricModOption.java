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

package org.quiltmc.loader.impl.plugin.fabric;

import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class FabricModOption extends InternalModOptionBase {

	public FabricModOption(QuiltPluginContext pluginContext, FabricLoaderModMetadata meta, Path from,
		QuiltLoaderIcon fileIcon, Path resourceRoot, boolean mandatory, boolean requiresRemap, @Nullable ModLoadOption containingMod) {

		super(pluginContext, meta.asQuiltModMetadata(), from, fileIcon, resourceRoot, mandatory, requiresRemap, containingMod);
	}

	@Override
	public QuiltLoaderIcon modTypeIcon() {
		return QuiltLoaderGui.iconFabric();
	}

	@Override
	protected String nameOfType() {
		return "fabric";
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return new FabricModContainer(pluginContext, metadata, from, transformedResourceRoot);
	}
}
