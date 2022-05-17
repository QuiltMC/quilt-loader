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

package org.quiltmc.loader.impl.plugin.quilt;

import java.nio.file.Path;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

/** A mod that is provided from the jar of a different mod. */
public class ProvidedModOption extends ModLoadOption implements AliasedLoadOption {
	final ModLoadOption provider;
	final ProvidedMod provided;

	public ProvidedModOption(ModLoadOption provider, ProvidedMod provided) {
		this.provider = provider;
		this.provided = provided;
	}

	@Override
	public String group() {
		return provided.group().isEmpty() ? super.group() : provided.group();
	}

	@Override
	public String id() {
		return provided.id();
	}

	@Override
	public Version version() {
		return provided.version();
	}

	@Override
	public boolean isMandatory() {
		return provider.isMandatory();
	}

	@Override
	public String toString() {
		return "{ProvidedModOption '" + id() + " " + version() + "' by " + provider + " }";
	}

	@Override
	public String shortString() {
		return "provided mod '" + id() + "' from " + provider.shortString();
	}

	@Override
	public String getSpecificInfo() {
		return provider.getSpecificInfo();
	}

	@Override
	public LoadOption getTarget() {
		return provider;
	}

	@Override
	public QuiltPluginContext loader() {
		return provider.loader();
	}

	@Override
	public ModMetadataExt metadata() {
		return provider.metadata();
	}

	@Override
	public Path from() {
		return provider.from();
	}

	@Override
	public Path resourceRoot() {
		return provider.resourceRoot();
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return provider.modTypeIcon();
	}
}
