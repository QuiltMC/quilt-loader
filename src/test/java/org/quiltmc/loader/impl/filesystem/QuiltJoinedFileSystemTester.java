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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class QuiltJoinedFileSystemTester {
	@ParameterizedTest
	@MethodSource("getPaths")
	public void test(Path rw1, Path rw2) throws IOException {
		// note, rw2 is mounted at the "sub" subdirectory in the joined filesystem
		try (QuiltJoinedFileSystem jfs = new QuiltJoinedFileSystem(
				"jfs", Arrays.asList(rw1, rw2.resolve("sub"))
		)) {
			Assertions.assertFalse(Files.newDirectoryStream(rw1).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(rw2).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1));
			assertSetsEqual(getChildren(rw2));
			assertSetsEqual(getChildren(jfs.root));

			createTestFile(rw1, "greeting.txt");

			Assertions.assertTrue(Files.newDirectoryStream(rw1).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(rw2).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1), rw1.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2));
			assertSetsEqual(getChildren(jfs.root), jfs.root.resolve("greeting.txt"));

			createTestFile(rw2, "greetings2.txt");

			Assertions.assertTrue(Files.newDirectoryStream(rw1).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(rw2).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1), rw1.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2), rw2.resolve("greetings2.txt"));
			assertSetsEqual(getChildren(jfs.root), jfs.root.resolve("greeting.txt"));

			Files.createDirectory(rw2.resolve("sub"));
			createTestFile(rw2, "sub/greetings3.txt");

			Assertions.assertTrue(Files.newDirectoryStream(rw1).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(rw2).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1), rw1.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2), rw2.resolve("greetings2.txt"), rw2.resolve("sub"));
			assertSetsEqual(
					getChildren(jfs.root), jfs.root.resolve("greeting.txt"), jfs.root.resolve("greetings3.txt")
			);
		}
	}

	@ParameterizedTest
	@MethodSource("getPaths")
	public void testDirectoryStream(Path rw1, Path rw2) throws IOException {
		try (QuiltJoinedFileSystem jfs = new QuiltJoinedFileSystem(
				"jfs", Arrays.asList(rw1, rw2)
		)) {
			createTestFile(rw1, "a.txt");
			createTestFile(rw2, "b.txt");

			DirectoryStream<Path> stream = Files.newDirectoryStream(jfs.root);
			Assertions.assertEquals(toSet(stream).size(), 2);
			stream.close();
		}
	}

	/**
	 * Tests directory streams in an environment where the filesystems have different layouts.
	 * For example, the folder you're streaming can exist in one filesystem but not another.
	 */
	@ParameterizedTest
	@MethodSource("getPaths")
	public void testMismatchedDirectoryStream(Path rw1, Path rw2) throws IOException {
		try (QuiltJoinedFileSystem jfs = new QuiltJoinedFileSystem(
				"jfs", Arrays.asList(rw1, rw2)
		)) {
			// Create a directory in rw2 but not in rw1 and iterate it via the joined filesystem
			Files.createDirectory(rw2.resolve("streamTest"));
			createTestFile(rw2, "streamTest/a.txt");

			assertEqual(
					Files.newDirectoryStream(jfs.root.resolve("streamTest")),
					jfs.root.resolve("streamTest/a.txt"));

			// Create a file in rw1 with the same name as the directory in rw2 and assert that this is ignored
			createTestFile(rw1, "streamTest");

			assertEqual(
					Files.newDirectoryStream(jfs.root.resolve("streamTest")),
					jfs.root.resolve("streamTest/a.txt"));

			// Explicitly test the closing of directory streams
			Files.newDirectoryStream(jfs.root.resolve("streamTest")).close();
		}
	}

	/**
	 * @return sets of two paths for testing joined filesystems
	 */
	public static Stream<Arguments> getPaths() throws IOException{
		QuiltMemoryFileSystem.ReadWrite memoryFs1 = new QuiltMemoryFileSystem.ReadWrite("mem1", true);
		QuiltMemoryFileSystem.ReadWrite memoryFs2 = new QuiltMemoryFileSystem.ReadWrite("mem2", true);

		Path tempFs1 = Files.createTempDirectory("temp1");
		Path tempFs2 = Files.createTempDirectory("temp2");

		return Stream.of(
				Arguments.of(memoryFs1.root, memoryFs2.root),
				Arguments.of(tempFs1, tempFs2)
		);
	}

	private static <T> void assertSetsEqual(Set<T> actual, T... expected) {
		Set<T> expectedSet = new HashSet<>();
		Collections.addAll(expectedSet, expected);
		Assertions.assertEquals(expectedSet, actual);
	}

	private static <T> void assertEqual(DirectoryStream<T> actual, T... expected) throws IOException {
		assertSetsEqual(toSet(actual), expected);
	}

	private static <T> Set<T> toSet(DirectoryStream<T> stream) throws IOException {
		Set<T> set = new HashSet<>();
		stream.forEach(set::add);
		stream.close();
		return set;
	}

	private static void createTestFile(Path base, String name) throws IOException {
		try (BufferedWriter bw = Files.newBufferedWriter(base.resolve(name))) {
			bw.write("this is a test file");
		}
	}

	private static Set<Path> getChildren(Path root) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
			Set<Path> set = new HashSet<>();
			for (Path path : stream) {
				set.add(path);
			}
			return set;
		}
	}
}
