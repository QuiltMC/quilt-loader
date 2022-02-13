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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class QuiltJoinedFileSystemTester {

	@Test
	public void testMemoryJoined() throws IOException {
		try (QuiltMemoryFileSystem.ReadWrite rw1 = new QuiltMemoryFileSystem.ReadWrite("rw1"); //
			QuiltMemoryFileSystem.ReadWrite rw2 = new QuiltMemoryFileSystem.ReadWrite("rw2"); //
			QuiltJoinedFileSystem jfs = new QuiltJoinedFileSystem(
				"jfs1", Arrays.asList(rw1.root, rw2.root.resolve("sub"))
			)//
		) {
			Assertions.assertFalse(Files.newDirectoryStream(rw1.root).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(rw2.root).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1.root));
			assertSetsEqual(getChildren(rw2.root));
			assertSetsEqual(getChildren(jfs.root));

			try (BufferedWriter bw = Files.newBufferedWriter(rw1.getPath("greeting.txt"))) {
				bw.write("hello");
			}

			Assertions.assertTrue(Files.newDirectoryStream(rw1.root).iterator().hasNext());
			Assertions.assertFalse(Files.newDirectoryStream(rw2.root).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1.root), rw1.root.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2.root));
			assertSetsEqual(getChildren(jfs.root), jfs.root.resolve("greeting.txt"));

			try (BufferedWriter bw = Files.newBufferedWriter(rw2.getPath("greetings2.txt"))) {
				bw.write("hello");
			}

			Assertions.assertTrue(Files.newDirectoryStream(rw1.root).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(rw2.root).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1.root), rw1.root.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2.root), rw2.root.resolve("greetings2.txt"));
			assertSetsEqual(getChildren(jfs.root), jfs.root.resolve("greeting.txt"));

			Path greetings3path = rw2.getPath("sub", "greetings3.txt");
			Files.createDirectory(greetings3path.getParent());
			try (BufferedWriter bw = Files.newBufferedWriter(greetings3path)) {
				bw.write("hello");
			}

			Assertions.assertTrue(Files.newDirectoryStream(rw1.root).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(rw2.root).iterator().hasNext());
			Assertions.assertTrue(Files.newDirectoryStream(jfs.root).iterator().hasNext());

			assertSetsEqual(getChildren(rw1.root), rw1.root.resolve("greeting.txt"));
			assertSetsEqual(getChildren(rw2.root), rw2.root.resolve("greetings2.txt"), rw2.root.resolve("sub"));
			assertSetsEqual(
				getChildren(jfs.root), jfs.root.resolve("greeting.txt"), jfs.root.resolve("greetings3.txt")
			);
		}
	}

	private static <T> void assertSetsEqual(Set<T> actual, T... expected) {
		Set<T> expectedSet = new HashSet<>();
		Collections.addAll(expectedSet, expected);
		Assertions.assertEquals(expectedSet, actual);
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
