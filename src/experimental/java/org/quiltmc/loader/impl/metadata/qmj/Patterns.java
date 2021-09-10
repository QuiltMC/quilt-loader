package org.quiltmc.loader.impl.metadata.qmj;

import java.util.regex.Pattern;

/**
 * Patterns used to match strings specified in {@code quilt.mod.json} files.
 */
final class Patterns {
	public static final Pattern VALID_MOD_ID = Pattern.compile("^[a-z][a-z0-9-_]{1,63}$");
	public static final Pattern VALID_MAVEN_GROUP = Pattern.compile("^[a-zA-Z0-9-_.]+$");
	public static final Pattern VERSION_PLACEHOLDER = Pattern.compile("^[a-zA-Z0-9-_.]+$");

	private Patterns() {
	}
}
