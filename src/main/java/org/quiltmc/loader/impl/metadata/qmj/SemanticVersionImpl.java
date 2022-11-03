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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Arrays;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

public class SemanticVersionImpl implements Version.Semantic {
	private final String raw;
	private final int[] components;
	private final String preRelease;
	private final String buildMeta;

	private static final Pattern UNSIGNED_INTEGER = Pattern.compile("0|[1-9][0-9]*");
	private static final Pattern DOT_SEPARATED_ID = Pattern.compile("|[-0-9A-Za-z]+(\\.[-0-9A-Za-z]+)*");

	public static SemanticVersionImpl of(String raw) throws VersionFormatException {
		return ofInternal(raw, false);
	}

	public static SemanticVersionImpl ofFabricPermittingWildcard(String raw) throws VersionFormatException {
		return ofInternal(raw, true);
	}

	private static SemanticVersionImpl ofInternal(String raw, boolean permitWildcard) throws VersionFormatException {
		String build;
		String prerelease;
		String semantic = raw;
		int buildDelimPos = semantic.indexOf('+');

		if (buildDelimPos >= 0) {
			build = semantic.substring(buildDelimPos + 1);
			semantic = semantic.substring(0, buildDelimPos);
		} else {
			build = "";
		}

		int dashDelimPos = semantic.indexOf('-');

		if (dashDelimPos >= 0) {
			prerelease = semantic.substring(dashDelimPos + 1);
			semantic = semantic.substring(0, dashDelimPos);

			if (prerelease.isEmpty()) {
				prerelease = EMPTY_BUT_PRESENT_PRERELEASE;
			}
		} else {
			prerelease = "";
		}

		if (!prerelease.isEmpty() && !DOT_SEPARATED_ID.matcher(prerelease).matches()) {
			throw new VersionFormatException("Invalid prerelease string '" + prerelease + "'!");
		}

		if (semantic.endsWith(".")) {
			throw new VersionFormatException("Negative raw number component found!");
		} else if (semantic.startsWith(".")) {
			throw new VersionFormatException("Missing raw component!");
		}

		String[] componentStrings = semantic.split("\\.");

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
				if (permitWildcard && ("x".equalsIgnoreCase(compStr) || "*".equals(compStr))) {
					components[i] = SemanticVersion.COMPONENT_WILDCARD;
					if (i != components.length - 1) {
						throw new VersionFormatException("Interjacent wildcard (1.x.2) are disallowed!");
					}
				} else {
					components[i] = Integer.parseInt(compStr);
					if (components[i] < 0) {
						throw new VersionFormatException("Negative raw number component '" + compStr + "'!");
					}
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
		return pos >= components.length ? 0 : components[pos];
	}

	@Override
	public int[] versionComponents() {
		return Arrays.copyOf(components, components.length);
	}

	private SemanticVersionImpl(String raw, int[] components, String preRelease, String buildMeta) {
		this.raw = Objects.requireNonNull(raw, "raw");
		this.components = Objects.requireNonNull(components, "components");
		this.preRelease = Objects.requireNonNull(preRelease, "preRelease");
		this.buildMeta = Objects.requireNonNull(buildMeta, "buildMeta");
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

	@Override
	public int compareTo(Version other) {
		Objects.requireNonNull(other, "other");
		if (other.isSemantic()) {
			return compareSemantic(other.semantic());
		} else {
			return GenericVersionImpl.compareRaw(raw, other.raw());
		}
	}

	@Override
	public int compareTo(Semantic other) {
		Objects.requireNonNull(other, "other");
		return compareSemantic(other);
	}

	private int compareSemantic(Version.Semantic o) {
		for (int i = 0; i < Math.max(this.versionComponentCount(), o.versionComponentCount()); i++) {
			int first = versionComponent(i);
			int second = o.versionComponent(i);

			int compare = Integer.compare(first, second);
			if (compare != 0) return compare;
		}


		if (isPreReleasePresent() || o.isPreReleasePresent()) {
			if (isPreReleasePresent() && o.isPreReleasePresent()) {
				StringTokenizer prereleaseATokenizer = new StringTokenizer(preRelease(), ".");
				StringTokenizer prereleaseBTokenizer = new StringTokenizer(o.preRelease(), ".");

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
			} else if (isPreReleasePresent()) {
				return -1;
			} else { // o.isPreReleasePresent()
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
		return Arrays.deepHashCode(new Object[] { raw, components, preRelease, buildMeta });
	}

	@Override
	public String toString() {
		return raw;
	}
}
