package org.quiltmc.loader.impl.metadata.qmj;

import org.apache.commons.lang3.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;

import java.util.Arrays;
import java.util.Iterator;

// TODO: investigate regex and StringTokenizer once we're able to actually test this code
public class SemanticVersionImpl implements Version.Semantic {
	private final String raw;
	private final int major;
	private final int minor;
	private final int patch;
	private final String preRelease;
	private final String buildMeta;

	public static SemanticVersionImpl of(String raw) throws VersionFormatException {
		int major;
		int minor;
		int patch;
		String preRelease = "";
		String buildMeta = "";

		// TODO: clean up logic
		int metaIndex = raw.indexOf('+');
		int preIndex = raw.indexOf('-');
		if (metaIndex != -1) {
			buildMeta = raw.substring(metaIndex);
			if (preIndex != -1) {
				preRelease = raw.substring(preIndex, metaIndex);
			}
		} else if (preIndex != -1) {
			preRelease = raw.substring(preIndex);
		}

		if (preIndex != -1) {
			raw = raw.substring(0, 1);
		}
		String[] versions = raw.split("\\.");
		if (versions.length != 3) {
			throw new VersionFormatException("Expected version to have 3 dot-separated numbers, got " + versions.length);
		}
		major = parsePositiveIntSafe(versions[0]);
		minor = parsePositiveIntSafe(versions[1]);
		patch = parsePositiveIntSafe(versions[2]);
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

	public SemanticVersionImpl(String raw, int major, int minor, int patch, String preRelease, String buildMeta) throws VersionFormatException {
		if (major < 0) {
			throw new VersionFormatException("Expected positive major version, got " + major);
		} else if (minor < 0) {
			throw new VersionFormatException("Expected positive minor version, got " + minor);
		} else if (patch < 0) {
			throw new VersionFormatException("Expected positive patch version, got " + patch);
		}
		checkAllowedChars(preRelease);
		checkAllowedChars(buildMeta);
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

	private static void checkAllowedChars(String str) throws VersionFormatException {
		for (char c : str.toCharArray()) {
			if (!CharUtils.isAsciiAlphanumeric(c) && c != '-' && c != '+') {
				throw new VersionFormatException("Illegal char " + c + " in string " + str);
			}
		}
	}
	private static int parsePositiveIntSafe(String bit) throws VersionFormatException {
		Integer ret = parsePositiveIntNullable(bit);
		if (ret == null) {
			throw new VersionFormatException("String " + bit + " is not a positive number!");
		}

		return ret;
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
