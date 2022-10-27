/*
 * Copyright 2016 FabricMC
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

package net.fabricmc.loader.api;

import org.quiltmc.loader.impl.fabric.util.version.Quilt2FabricVersion;

import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * Represents a version of a mod.
 *
 * @see ModMetadata#getVersion()
 */
public interface Version extends Comparable<Version> {
	/**
	 * Returns the user-friendly representation of this version.
	 */
	String getFriendlyString();

	/**
	 * Parses a version from a string notation.
	 *
	 * @param string the string notation of the version
	 * @return the parsed version
	 * @throws VersionParsingException if a problem arises during version parsing
	 */
	static Version parse(String string) throws VersionParsingException {
		if (string == null || string.isEmpty()) {
			throw new VersionParsingException("Version must be a non-empty string!");
		}
		return Quilt2FabricVersion.toFabric(org.quiltmc.loader.api.Version.of(string));
	}
}
