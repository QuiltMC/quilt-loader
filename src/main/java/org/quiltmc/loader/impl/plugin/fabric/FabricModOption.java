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

package org.quiltmc.loader.impl.plugin.fabric;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class FabricModOption extends InternalModOptionBase {

	public FabricModOption(QuiltPluginContext pluginContext, FabricLoaderModMetadata meta, Path from,
		PluginGuiIcon fileIcon, Path resourceRoot, boolean mandatory, boolean requiresRemap) {

		super(pluginContext, meta.asQuiltModMetadata(), from, fileIcon, resourceRoot, mandatory, requiresRemap);
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_FABRIC;
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		return new FabricModContainer(pluginContext, metadata, from, transformedResourceRoot);
	}
}
