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

package org.quiltmc.test;

import org.junit.jupiter.api.*;
import org.quiltmc.loader.impl.metadata.FabricModMetadataReader;
import org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ModJsonParsingTests {
	private static Path testLocation;
	private static Path specPath;
	private static Path errorPath;

	@BeforeAll
	public static void setupPaths() {
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

	@TestFactory
	Stream<DynamicTest> autoTests() throws IOException {
		Path loc = testLocation.resolve("auto");
		Stream<DynamicTest> spec = DynamicTest.stream(Files.walk(loc.resolve("spec")).filter(path -> !Files.isDirectory(path) && path.toString().endsWith(".json")),
				p -> p.getFileName().toString(), p -> ModMetadataReader.read(p));
		Stream<DynamicTest> error =	DynamicTest.stream(Files.walk(loc.resolve("error")).filter(path -> !Files.isDirectory(path) && path.toString().endsWith(".json")),
				p -> p.getFileName().toString(), p -> {
					try {
						ModMetadataReader.read(p);
						Assertions.fail("Erroneous quilt.mod.json was parsed successfully (" + p + ")");
					} catch (Exception ex) {
						// do nothing
					}
				});
		return Stream.concat(spec, error);
	}
}