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

package org.quiltmc.loader.api.version;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.qmj.GenericVersionImpl;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;

import java.util.Arrays;
import java.util.stream.Collectors;

@ApiStatus.NonExtendable
public interface Version {
	static Version of(String raw) {
		try {
			return Semantic.of(raw);
		} catch (VersionFormatException ex) {
			return new GenericVersionImpl(raw);
		}
	}

	String raw();

	default boolean isSemantic() {
		return this instanceof Version.Semantic;
	}


	/**
	 * @throws ClassCastException if the version is not a semantic version
	 */
	default Semantic semantic() {
		return (Semantic) this;
	}

	/**
	 * A string version, sorted with {@link String#compareTo(Object) String.compareTo(String)}.
	 */
	interface Raw extends Version {
	}

	/**
	 * Representation of a semantic version, but with an arbitrary number of components.
	 * <p>Requesting components greater than the number of components in the version will return {@code 0}.
	 */
	@ApiStatus.NonExtendable
	interface Semantic extends Version {
		static Semantic of(String raw) throws VersionFormatException {
			return SemanticVersionImpl.of(raw);
		}

		static Semantic of(int[] components, @Nullable String preRelease, @Nullable String buildMetadata) throws VersionFormatException {
			StringBuilder raw = new StringBuilder();
			raw.append(Arrays.stream(components).mapToObj(Integer::toString).collect(Collectors.joining("."))); // 1.0.0

			if (preRelease != null) {
				raw.append('-').append(preRelease);
			}

			if (buildMetadata != null) {
				raw.append('+').append(buildMetadata);
			}
			// HACK: re-building raw makes sure that everything is verified to be valid
			return SemanticVersionImpl.of(raw.toString());
		}

		/**
		 * Returns the number of components in this version.
		 *
		 * <p>For example, {@code 1.3.x} has 3 components.</p>
		 *
		 * @return the number of components
		 */
		int versionComponentCount();

		/**
		 * Returns the version component at {@code pos}.
		 * If {@code pos} is greater than or equal to the number of components, then it returns {@code 0}.
		 * @param pos the position to check
		 * @return the version component
		 */
		int versionComponent(int pos);

		int[] versionComponents();

		@Nullable
		String preRelease();

		@Nullable
		String buildMetadata();

		int compareTo(Semantic semantic);
	}
}
