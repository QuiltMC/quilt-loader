package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.impl.metadata.qmj.GenericVersionImpl;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;

/**
 * Representation of a version.
 */
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
