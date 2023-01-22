/*
 * Copyright 2022, 2023 QuiltMC
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

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public class VersionParsingTests extends JsonTestBase {
	static void quilt(String raw) {
		System.out.println("Checking pass: " + raw);
		try {
			SemanticVersionImpl.of(raw);
		} catch (VersionFormatException e) {
			Assertions.fail(e);
		} catch (Throwable t) {
			Assertions.fail("Uncaught exception ", t);
		}
	}
	static void quiltFails(String raw) {
		System.out.println("Checking fails: " + raw);
		try {
			SemanticVersionImpl.of(raw);
			Assertions.fail("invalid version " + raw + " was parsed successfully?");
		} catch (VersionFormatException e) {
			//
		}
	}

	static void fabric(String raw) {
		System.out.println("Checking pass: " + raw);
		try {
			SemanticVersion.parse(raw);
		} catch (VersionParsingException e) {
			Assertions.fail(e);
		}
	}

	static void fabricFails(String raw) {
		System.out.println("Checking fails: " + raw);
		try {
			SemanticVersion.parse(raw);
			Assertions.fail("Invalid version " + raw + " was parsed successfully?");
		} catch (VersionParsingException e) {
			//
		}
	}
	@Test
	void quiltParser() throws IOException {
		JsonReader pass = get("testing/version/passing.json");
		pass.beginArray();
		while (pass.hasNext()) {
			quilt(pass.nextString());
		}
		pass.close();

		JsonReader fail = get("testing/version/failing.json");
		fail.beginArray();
		while (fail.hasNext()) {
			quiltFails(fail.nextString());
		}
		fail.close();
	}

	@Test
	void fabricParser() throws IOException {
		JsonReader pass = get("testing/version/passing.json");
		pass.beginArray();
		while (pass.hasNext()) {
			fabric(pass.nextString());
		}
		pass.close();

		JsonReader fail = get("testing/version/failing.json");
		fail.beginArray();
		while (fail.hasNext()) {
			fabricFails(fail.nextString());
		}
		fail.close();
	}

}
