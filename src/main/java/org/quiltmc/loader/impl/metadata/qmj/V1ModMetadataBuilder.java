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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModLoadType;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.metadata.ModEnvironment;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class V1ModMetadataBuilder {
	/* Required fields */
	public ObjectImpl root = new ObjectImpl("root", new HashMap<>());
	public String id;
	public String group;
	public Version version;
	/* Optional fields */
	public @Nullable String name;
	public @Nullable String description;
	public final List<ModLicense> licenses = new ArrayList<>();
	public final List<ModContributor> contributors = new ArrayList<>();
	public final Map<String, String> contactInformation = new LinkedHashMap<>();
	public final List<ModDependency> depends = new ArrayList<>();
	public final List<ModDependency> breaks = new ArrayList<>();
	public String intermediateMappings;
	public @Nullable Icons icons;
	/* Internal fields */
	public ModLoadType loadType = ModLoadType.IF_REQUIRED;
	public final List<ProvidedMod> provides = new ArrayList<>();
	public final Map<String, List<ModEntrypoint>> entrypoints = new LinkedHashMap<>();
	public final List<String> jars = new ArrayList<>();
	public final Map<String, String> languageAdapters = new LinkedHashMap<>();
	public final List<String> repositories = new ArrayList<>();
	/* TODO: Move to plugins */
	public final Map<EnvType, List<String>> mixins = new HashMap<>();
	public final List<String> accessWideners = new ArrayList<>();
	public ModEnvironment env = ModEnvironment.UNIVERSAL;

	public InternalModMetadata build() {
		return new V1ModMetadataImpl(this);
	}

	public void setRoot(ObjectImpl root) {
		this.root = root;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public void setName(@Nullable String name) {
		this.name = name;
	}

	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	public void setIntermediateMappings(String intermediateMappings) {
		this.intermediateMappings = intermediateMappings;
	}

	public void setIcons(@Nullable Icons icons) {
		this.icons = icons;
	}

	public void setLoadType(ModLoadType loadType) {
		this.loadType = loadType;
	}

	public void setEnv(ModEnvironment env) {
		this.env = env;
	}
}
