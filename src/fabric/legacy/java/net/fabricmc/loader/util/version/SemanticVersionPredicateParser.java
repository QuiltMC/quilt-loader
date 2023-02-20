package net.fabricmc.loader.util.version;


import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.function.Predicate;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public final class SemanticVersionPredicateParser {
	public static Predicate<SemanticVersionImpl> create(String text) throws VersionParsingException {
		VersionPredicate predicate = VersionPredicate.parse(text);

		return predicate::test;
	}
}
