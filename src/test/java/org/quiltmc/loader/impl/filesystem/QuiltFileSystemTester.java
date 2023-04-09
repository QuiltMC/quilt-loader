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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class QuiltFileSystemTester {

	@ParameterizedTest
	@ValueSource(strings = { "buildcraft-9.0.0.jar", "extrasounds-2.3.11.19.2-1.19.1~akemi-git-c96fd99.jar" })
	public void testFilesystemNames(String name) {
		try (QuiltMemoryFileSystem mfs = new QuiltMemoryFileSystem.ReadWrite(name, true)) {
			mfs.getRoot();
		}
	}

	@Test
	public void testPathTraversal() {
		try (QuiltMemoryFileSystem fs = new QuiltMemoryFileSystem.ReadWrite("test_basics", true)) {
			testUnixLikeFileSystem(fs);
		}
	}

	@Test
	public void testGlob() throws IOException {
		try (QuiltMemoryFileSystem fs = new QuiltMemoryFileSystem.ReadWrite("test_basics", true)) {
			Path root = fs.root;

			Files.createFile(root.resolve("hello"));
			Files.createFile(root.resolve("hello.txt"));
			Files.createFile(root.resolve("hello.java"));
			Files.createFile(root.resolve("Files.java"));
			Files.createFile(root.resolve("Files.class"));

			Map<String, Set<String>> expected = new HashMap<>();
			expected.put("*", set("/hello", "/hello.txt", "/hello.java", "/Files.java", "/Files.class"));
			expected.put("*.java", set("/hello.java", "/Files.java"));
			expected.put("hello.*", set("/hello.txt", "/hello.java"));
			expected.put("hello.{txt,java}", set("/hello.txt", "/hello.java"));

			for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, entry.getKey())) {
					Set<String> set = new HashSet<>();
					for (Path p : stream) {
						set.add(p.toString());
					}
					eq(entry.getValue(), set);
				}
			}
		}
	}

	@SafeVarargs
	private static <T> Set<T> set(T... values) {
		Set<T> set = new HashSet<>();
		Collections.addAll(set, values);
		return set;
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
