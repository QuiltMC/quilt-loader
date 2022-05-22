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

package org.quiltmc.loader.api;

import net.fabricmc.loader.api.VersionParsingException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.impl.metadata.qmj.GenericVersionImpl;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;
import org.quiltmc.loader.impl.util.version.FabricSemanticVersionImpl;

/**
 * Representation of a version.
 */
@ApiStatus.NonExtendable
public interface Version {
	static Version of(String raw) {
		try {
			return Semantic.of(raw);
		} catch (VersionFormatException ex) {
			try {
				// this will be removed when we remove fabric support from quilt-loader
				return new FabricSemanticVersionImpl(raw, false);
			} catch (VersionParsingException e) {
				return new GenericVersionImpl(raw);
			}
		}
	}

	String raw();

	default boolean isSemantic() {
		return this instanceof Version.Semantic;
	}

	default Semantic semantic() {
		return (Semantic) this;
	}
	/**
	 * Representation of a semantic version
	 */
	@ApiStatus.NonExtendable
	interface Semantic extends Version, Comparable<Semantic> {
		static Semantic of(String raw) throws VersionFormatException {
			return SemanticVersionImpl.of(raw);
		}

		static Semantic of(int major, int minor, int patch, String preRelease, String buildMetadata) throws VersionFormatException {
			return SemanticVersionImpl.of(major, minor, patch, preRelease, buildMetadata);
		}

		// TODO: maybe allow returning negative when ${version} is provided?
		/**
		 * Must be a positive integer.
		 */
		int major();

		/**
		 * Must be a positive integer.
		 */
		int minor();

		/**
		 * Must be a positive integer.
		 */
		int patch();

		/**
		 * Returns an empty string if not applicable
		 */
		String preRelease();

		/**
		 * Returns an empty string if not applicable
		 */
		String buildMetadata();

		// TODO: docs
		@Override
		int compareTo(@NotNull Version.Semantic o);
	}
}
