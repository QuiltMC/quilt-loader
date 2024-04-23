package net.fabricmc.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

public class VersionPredicateTests {
	@Test
	public void testPredicates() throws VersionParsingException {
		VersionPredicate predicate = VersionPredicate.parse("2.x.x");
		Version version = Version.parse("2.0.0-beta3");
		Assertions.assertTrue(predicate.test(version), predicate + " does not allow " + version);
	}
}
