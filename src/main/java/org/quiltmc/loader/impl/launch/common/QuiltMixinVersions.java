/*
 * Copyright 2016 FabricMC
 * Copyright 2026 QuiltMC
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

package org.quiltmc.loader.impl.launch.common;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuiltMixinVersions {
	private static final List<LoaderMixinVersionEntry> versions = new ArrayList<>();
	private static final Map<Integer, String> minFabricLoaderVersions = new HashMap<>();
	private static final Map<Integer, String> minQuiltLoaderVersions = new HashMap<>();

	static {
		// maximum loader version and bundled fabric mixin version, DESCENDING ORDER, LATEST FIRST
		// loader versions with new mixin versions need to be added here

		addVersion("0.19.0", "0.31.0-beta.1", FabricUtil.COMPATIBILITY_0_17_1);
		addVersion("0.18.4", "0.30.0-beta.2", FabricUtil.COMPATIBILITY_0_17_0);
		addVersion("0.17.3", "0.29.3-beta.1", FabricUtil.COMPATIBILITY_0_16_5);
		addVersion("0.16.0", "0.26.2", FabricUtil.COMPATIBILITY_0_14_0);
		addVersion("0.12.0-", "0.12.0", FabricUtil.COMPATIBILITY_0_10_0);
	}

	static List<LoaderMixinVersionEntry> getVersions() {
		return versions;
	}

	public static String getMinFabricLoaderVersion(int mixinCompat) {
		return minFabricLoaderVersions.get(mixinCompat);
	}

	public static String getMinQuiltLoaderVersion(int mixinCompat) {
		return minQuiltLoaderVersions.get(mixinCompat);
	}

	private static void addVersion(String minFabricLoaderVersion, String minQuiltLoaderVersion, int mixinCompat) {
		try {
			versions.add(new LoaderMixinVersionEntry(SemanticVersion.parse(minFabricLoaderVersion),
					Version.Semantic.of(minQuiltLoaderVersion), mixinCompat));
		} catch (VersionParsingException | VersionFormatException e) {
			throw new RuntimeException(e);
		}

		minFabricLoaderVersions.put(mixinCompat, minFabricLoaderVersion);
		minQuiltLoaderVersions.put(mixinCompat, minQuiltLoaderVersion);
	}

	static final class LoaderMixinVersionEntry {
		final SemanticVersion fabricLoaderVersion;
		final Version.Semantic quiltLoaderVersion;
		final int mixinVersion;

		LoaderMixinVersionEntry(SemanticVersion fabricLoaderVersion, Version.Semantic quiltLoaderVersion, int mixinVersion) {
			this.fabricLoaderVersion = fabricLoaderVersion;
			this.quiltLoaderVersion = quiltLoaderVersion;
			this.mixinVersion = mixinVersion;
		}
	}
}
