package net.fabricmc.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class EntrypointTest {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final CustomEntry FIELD_ENTRY = EntrypointTest::fieldEntry;

	public static String staticEntry() {
		return "static";
	}

	public EntrypointTest() {
		LOGGER.info("EntrypointTest instance created");
	}

	public String instanceEntry() {
		return "instance";
	}

	public static String fieldEntry() {
		return "field";
	}
}
