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

package org.quiltmc.loader.impl.plugin.quilt;

import java.io.IOException;
import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.plugin.base.InternalModOptionBase;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class BuiltinModOption extends InternalModOptionBase {

	public BuiltinModOption(QuiltPluginContext pluginContext, InternalModMetadata meta, Path from, Path resourceRoot) {
		super(pluginContext, meta, from, GuiManagerImpl.ICON_JAVA_PACKAGE, resourceRoot, true, false);
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_JAVA_PACKAGE;
	}

	@Override
	public PluginGuiIcon modCompleteIcon() {
		return GuiManagerImpl.ICON_JAVA_PACKAGE;
	}

	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot) {
		if (!transformedResourceRoot.equals(resourceRoot)) {
			try {
				resourceRoot.getFileSystem().close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return new BuiltinModContainer(pluginContext, metadata, from, transformedResourceRoot, needsChasmTransforming());
	}
}
