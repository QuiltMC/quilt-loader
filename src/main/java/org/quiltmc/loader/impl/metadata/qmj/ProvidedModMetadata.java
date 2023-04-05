/*
 * Copyright 2023 QuiltMC
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import net.fabricmc.api.EnvType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ProvidedModMetadata implements ModMetadataExt {

	final ProvidedMod override;
	final ModMetadataExt metadata;

	public ProvidedModMetadata(ProvidedMod override, ModMetadataExt metadata) {
		this.override = override;
		this.metadata = metadata;
	}

	@Override
	public String id() {
		return override.id();
	}

	@Override
	public String group() {
		return override.group();
	}

	@Override
	public Version version() {
		return override.version();
	}

	@Override
	public Collection<String> mixins(EnvType env) {
		return metadata.mixins(env);
	}

	@Override
	public String name() {
		return metadata.name();
	}

	@Override
	public Collection<String> accessWideners() {
		return metadata.accessWideners();
	}

	@Override
	public ModEnvironment environment() {
		return metadata.environment();
	}

	@Override
	public String description() {
		return metadata.description();
	}

	@Override
	public Collection<ModLicense> licenses() {
		return metadata.licenses();
	}

	@Override
	public boolean shouldQuiltDefineDependencies() {
		return metadata.shouldQuiltDefineDependencies();
	}

	@Override
	public Collection<ModContributor> contributors() {
		return metadata.contributors();
	}

	@Override
	public @Nullable String getContactInfo(String key) {
		return metadata.getContactInfo(key);
	}

	@Override
	public boolean shouldQuiltDefineProvides() {
		return metadata.shouldQuiltDefineProvides();
	}

	@Override
	public Map<String, String> contactInfo() {
		return metadata.contactInfo();
	}

	@Override
	public Collection<ModDependency> depends() {
		return metadata.depends();
	}

	@Override
	public ModLoadType loadType() {
		return metadata.loadType();
	}

	@Override
	public @Nullable ModPlugin plugin() {
		return metadata.plugin();
	}

	@Override
	public Collection<ModDependency> breaks() {
		return metadata.breaks();
	}

	@Override
	public Collection<? extends ProvidedMod> provides() {
		// Otherwise this mod will "provide" itself
		return Collections.emptyList();
	}

	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints() {
		return metadata.getEntrypoints();
	}

	@Override
	public Map<String, String> languageAdapters() {
		return metadata.languageAdapters();
	}

	@Override
	public @Nullable String icon(int size) {
		return metadata.icon(size);
	}

	@Override
	public boolean containsValue(String key) {
		return metadata.containsValue(key);
	}

	@Override
	public @Nullable LoaderValue value(String key) {
		return metadata.value(key);
	}

	@Override
	public Map<String, LoaderValue> values() {
		return metadata.values();
	}
}
