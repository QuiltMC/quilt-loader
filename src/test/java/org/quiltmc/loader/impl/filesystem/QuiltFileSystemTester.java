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

package org.quiltmc.loader.impl.filesystem;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class QuiltFileSystemTester {

	@Test
	public void testPathTraversal() {
		try (QuiltMemoryFileSystem fs = new QuiltMemoryFileSystem.ReadWrite("test_basics", true)) {
			testUnixLikeFileSystem(fs);
		}
	}

	/** Used to compare our file system implementation to a unix file system. This won't work when tested on a windows
	 * platform however. */
	@Test
	@EnabledOnOs(OS.LINUX)
	public void testLinuxPathTraversal() {
		testUnixLikeFileSystem(FileSystems.getDefault());
	}

	/** @param fs The {@link FileSystem} to test. This is <em>not</em> modified at all by this method - unlike the
	 *            others! */
	private static void testUnixLikeFileSystem(FileSystem fs) {
		Path root = fs.getRootDirectories().iterator().next();

		eq("/", root);
		_false(root.iterator().hasNext());

		Path absGreeting = root.resolve("greeting");
		Path absHello = absGreeting.resolve("hello");
		Path absHi = absGreeting.resolve("hi");

		Iterator<Path> iter = absGreeting.iterator();
		_true(iter.hasNext());
		eq("greeting", iter.next());
		_false(iter.hasNext());

		eq("/greeting", absGreeting);
		eq("/greeting/hello", absHello);
		eq("/greeting/hi", absHi);

		eq(2, absHello.getNameCount());
		eq("greeting", absHello.getName(0));
		eq("hello", absHello.getName(1));

		eq("hi", absGreeting.relativize(absHi));
		eq("../hi", absHello.relativize(absHi));

		Path relHello = fs.getPath("hello");
		Path relHi = fs.getPath("hi");

		eq("hello", relHello);
		eq("hi", relHi);

		eq("../hi", relHello.relativize(relHi));
		eq("../hello", relHi.relativize(relHello));

		Path longBase = fs.getPath("this", "is", "a", "very", "long", "path");
		eq(fs.getPath("this", "is", "short"), longBase.resolve("../../../../short").normalize());

		eq("greeting", absHello.subpath(0, 1));
		eq("greeting/hello", absHello.subpath(0, 2));
		eq("hello", absHello.subpath(1, 2));
	}

	private static void eq(int expected, int value) {
		Assertions.assertEquals(expected, value);
	}

	private static void _false(boolean value) {
		Assertions.assertFalse(value);
	}

	private static void _true(boolean value) {
		Assertions.assertTrue(value);
	}

	private static void eq(String expected, Object obj) {
		Assertions.assertEquals(expected, obj.toString());
	}

	private static void eq(Object expected, Object obj) {
		Assertions.assertEquals(expected, obj);
	}
}
