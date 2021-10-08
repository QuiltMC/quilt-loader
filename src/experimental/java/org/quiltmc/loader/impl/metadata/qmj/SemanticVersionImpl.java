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

package org.quiltmc.loader.impl.metadata.qmj;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: investigate regex and StringTokenizer once we're able to actually test this code
public class SemanticVersionImpl implements Version.Semantic {
	private final String raw;
	private final int major;
	private final int minor;
	private final int patch;
	private final String preRelease;
	private final String buildMeta;

	/**
	 * Capture groups 1, 2, 3: Major, minor, patch. Must be between 0 and 9 and of any length.
	 * Non-capturing group 1: Matches the start of build metadata
	 * Capture group 4:
	 */
	private static final Pattern SEMVER_MATCHER = Pattern.compile("^(?<major>[0-9]+)\\.(?<minor>[0-9]+)\\.(?<patch>[0-9]+)(?:-(?<pre>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+(?<meta>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

	public static SemanticVersionImpl of(String raw) throws VersionFormatException {
		int major;
		int minor;
		int patch;
		String preRelease;
		String buildMeta;
		Matcher matcher = SEMVER_MATCHER.matcher(raw);
		if (!matcher.matches()) {
			throw new VersionFormatException("Invalid SemVer string " + raw);
		}
		try {
			major = Integer.parseInt(matcher.group("major"));
			minor = Integer.parseInt(matcher.group("minor"));
			patch = Integer.parseInt(matcher.group("patch"));
		} catch (NumberFormatException ex) {
			// The regex doesn't catch having a number greater than Integer.MAX_INT
			throw new VersionFormatException("Invalid SemVer string " + raw);
		}
		preRelease = matcher.group("pre");
		buildMeta = matcher.group("meta");
		if (buildMeta == null) {
			buildMeta = "";
		}
		if (preRelease == null) {
			preRelease = "";
		}
		return new SemanticVersionImpl(raw, major, minor, patch, preRelease, buildMeta);
	}


	public static SemanticVersionImpl of(int major, int minor, int patch, String preRelease, String buildMeta) throws VersionFormatException {
		StringBuilder sb = new StringBuilder();
		sb.append(major).append('.').append(major).append('.').append(patch);
		if (!preRelease.isEmpty()) {
			sb.append('-').append(preRelease);
		}
		if (!buildMeta.isEmpty()) {
			sb.append('+').append(buildMeta);
		}
		return new SemanticVersionImpl(sb.toString(), major, minor, patch, preRelease, buildMeta);
	}

	private SemanticVersionImpl(String raw, int major, int minor, int patch, String preRelease, String buildMeta) throws VersionFormatException {
		if (!SEMVER_MATCHER.matcher(raw).matches()) {
			throw new VersionFormatException("SemVer string " + raw + " is invalid!");
		}

		// Since this is a private constructor it's safe to assume if the raw is valid the split up parts are valid
		this.raw = raw;
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.preRelease = preRelease;
		this.buildMeta = buildMeta;
	}

	@Override
	public String raw() {
		return raw;
	}

	@Override
	public int major() {
		return major;
	}

	@Override
	public int minor() {
		return minor;
	}

	@Override
	public int patch() {
		return patch;
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
	public int compareTo(@NotNull Semantic o) {
		if (this.major() != o.major()) {
			return this.major() - o.major();
		} else if (this.minor() != o.minor()) {
			return this.minor() - o.minor();
		} else if (this.patch() != o.patch()) {
			return this.patch() - o.patch();
		} else {
			// TODO: if this code gets hot we can reduce allocations and use a fancy for loop with more guards
			Iterator<String> leftIter = Arrays.stream(o.preRelease().split("\\.")).iterator();
			Iterator<String> rightIter = Arrays.stream(o.preRelease().split("\\.")).iterator();
			while (true) {
				if (!leftIter.hasNext() && !rightIter.hasNext()) {
					return 0;
				}
				// Longer takes precedence over smaller
				else if (leftIter.hasNext() && !rightIter.hasNext()) {
					return -1;
				} else if (!leftIter.hasNext()) {
					return 1;
				}
				String left = leftIter.next();
				String right = rightIter.next();
				Integer lInt = parsePositiveIntNullable(left);
				Integer rInt = parsePositiveIntNullable(right);
				if (lInt != null && rInt != null) {
					if (!left.equals(right)) {
						return lInt - rInt;
					}
				}
				// Numeric always has lower precedence
				else if (lInt != null) {
					return -1;
				} else if (rInt == null) {
					return 1;
				} else {
					int comp = left.compareTo(right);
					if (comp != 0) {
						return comp;
					}
				}
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SemanticVersionImpl that = (SemanticVersionImpl) o;
		return major == that.major && minor == that.minor && patch == that.patch && raw.equals(that.raw) && preRelease.equals(that.preRelease) && buildMeta.equals(that.buildMeta);
	}

	@Override
	public int hashCode() {
		return Objects.hash(raw, major, minor, patch, preRelease, buildMeta);
	}

	private static @Nullable Integer parsePositiveIntNullable(String bit) {
		if (bit.startsWith("+")) {
			return null;
		}
		try {
			return Integer.parseUnsignedInt(bit);
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
