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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.version.Version;
import org.quiltmc.loader.api.version.VersionFormatException;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

// TODO: investigate regex and StringTokenizer once we're able to actually test this code
public class SemanticVersionImpl implements Version.Semantic {
	private final String raw;
	private final int[] components;
	private final String preRelease;
	private final String buildMeta;

	private static final Pattern UNSIGNED_INTEGER = Pattern.compile("0|[1-9][0-9]*");
	private static final Pattern DOT_SEPARATED_ID = Pattern.compile("|[-0-9A-Za-z]+(\\.[-0-9A-Za-z]+)*");
	public static SemanticVersionImpl of(String raw) throws VersionFormatException {
		String build;
		String prerelease;
		int buildDelimPos = raw.indexOf('+');

		if (buildDelimPos >= 0) {
			build = raw.substring(buildDelimPos + 1);
			raw = raw.substring(0, buildDelimPos);
		} else {
			build = null;
		}

		int dashDelimPos = raw.indexOf('-');

		if (dashDelimPos >= 0) {
			prerelease = raw.substring(dashDelimPos + 1);
			raw = raw.substring(0, dashDelimPos);
		} else {
			prerelease = null;
		}

		if (prerelease != null && !DOT_SEPARATED_ID.matcher(prerelease).matches()) {
			throw new VersionFormatException("Invalid prerelease string '" + prerelease + "'!");
		}

		if (build != null && !DOT_SEPARATED_ID.matcher(build).matches()) {
			throw new VersionFormatException("Invalid build string '" + build + "'!");
		}

		if (raw.endsWith(".")) {
			throw new VersionFormatException("Negative raw number component found!");
		} else if (raw.startsWith(".")) {
			throw new VersionFormatException("Missing raw component!");
		}

		String[] componentStrings = raw.split("\\.");

		if (componentStrings.length < 1) {
			throw new VersionFormatException("Did not provide raw numbers!");
		}

		int[] components = new int[componentStrings.length];

		for (int i = 0; i < componentStrings.length; i++) {
			String compStr = componentStrings[i];

			if (compStr.trim().isEmpty()) {
				throw new VersionFormatException("Missing raw number component!");
			}

			try {
				components[i] = Integer.parseInt(compStr);

				if (components[i] < 0) {
					throw new VersionFormatException("Negative raw number component '" + compStr + "'!");
				}
			} catch (NumberFormatException e) {
				throw new VersionFormatException("Could not parse raw number component '" + compStr + "'!", e);
			}
		}
		return new SemanticVersionImpl(raw, components, prerelease, build);
	}

	@Override
	public int versionComponentCount() {
		return components.length;
	}

	@Override
	public int versionComponent(int pos) {
		return components[pos];
	}

	@Override
	public int[] versionComponents() {
		return Arrays.copyOf(components, components.length);
	}

	private SemanticVersionImpl(String raw, int[] components, @Nullable String preRelease, @Nullable String buildMeta) throws VersionFormatException {
		this.raw = raw;
		this.components = components;
		this.preRelease = preRelease;
		this.buildMeta = buildMeta;
	}

	@Override
	public String raw() {
		return raw;
	}

	@Override
	public String preRelease() {
		return preRelease;
	}

	@Override
	public String buildMetadata() {
		return buildMeta;
	}

	public int compareTo(@NotNull Semantic other) {
		Version.Semantic o = other;

		for (int i = 0; i < Math.max(this.versionComponentCount(), o.versionComponentCount()); i++) {
			int first = versionComponent(i);
			int second = o.versionComponent(i);

			int compare = Integer.compare(first, second);
			if (compare != 0) return compare;
		}

		Optional<String> prereleaseA = Optional.ofNullable(preRelease());
		Optional<String> prereleaseB = Optional.ofNullable(o.preRelease());

		if (prereleaseA.isPresent() || prereleaseB.isPresent()) {
			if (prereleaseA.isPresent() && prereleaseB.isPresent()) {
				StringTokenizer prereleaseATokenizer = new StringTokenizer(prereleaseA.get(), ".");
				StringTokenizer prereleaseBTokenizer = new StringTokenizer(prereleaseB.get(), ".");

				while (prereleaseATokenizer.hasMoreElements()) {
					if (prereleaseBTokenizer.hasMoreElements()) {
						String partA = prereleaseATokenizer.nextToken();
						String partB = prereleaseBTokenizer.nextToken();

						if (UNSIGNED_INTEGER.matcher(partA).matches()) {
							if (UNSIGNED_INTEGER.matcher(partB).matches()) {
								int compare = Integer.compare(partA.length(), partB.length());
								if (compare != 0) return compare;
							} else {
								return -1;
							}
						} else {
							if (UNSIGNED_INTEGER.matcher(partB).matches()) {
								return 1;
							}
						}

						int compare = partA.compareTo(partB);
						if (compare != 0) return compare;
					} else {
						return 1;
					}
				}

				return prereleaseBTokenizer.hasMoreElements() ? -1 : 0;
			} else if (prereleaseA.isPresent()) {
				return -1;
			} else { // prereleaseB.isPresent()
				return 1;
			}
		} else {
			return 0;
		}

	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof SemanticVersionImpl)) return false;
		SemanticVersionImpl that = (SemanticVersionImpl) o;
		return Objects.equals(raw, that.raw) && Arrays.equals(components, that.components) && Objects.equals(preRelease, that.preRelease) && Objects.equals(buildMeta, that.buildMeta);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(raw, preRelease, buildMeta);
		result = 31 * result + Arrays.hashCode(components);
		return result;
	}
}
