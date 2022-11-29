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

package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class ProvidedModImpl implements ProvidedMod {

	private final String group, id;
	private final Version version;

	public ProvidedModImpl(String group, String id, Version version) {
		this.group = group;
		this.id = id;
		this.version = version;
	}

	@Override
	public String group() {
		return group;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Version version() {
		return version;
	}

	@Override
	public String toString() {
		return "ProvidedMod { " + group + ":" + id + " v " + version + " }";
	}
}
