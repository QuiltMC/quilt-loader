package org.quiltmc.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.impl.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ModJsonParsingTests {
	private static final Logger LOGGER = LogManager.getLogger();
	private static Path testLocation;
	private static Path specPath;
	private static Path errorPath;

	@BeforeAll
	private static void setupPaths() {
		testLocation = new File(System.getProperty("user.dir"))
				.toPath()
				.resolve("src")
				.resolve("test")
				.resolve("resources")
				.resolve("testing")
				.resolve("parsing")
				.resolve("quilt")
				.resolve("v1");

		specPath = testLocation.resolve("spec");
		errorPath = testLocation.resolve("error");
	}

	@Test
	void optionalFieldsArentRequired() throws IOException {
		// In an early prototype some optional fields were accidentally required in dependency objects, this
		// tests that they are actually optional
		ModMetadataReader.read(LOGGER, testLocation.resolve("optional_fields.json"));
	}
}