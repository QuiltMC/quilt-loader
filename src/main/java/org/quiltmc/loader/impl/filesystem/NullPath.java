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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Optional;

/** An implementation of {@link Path} for use where neither null nor {@link Optional} can be used in place of a
 * {@link Path}. */
abstract class NullPath implements Path {

	/** @throws IllegalStateException always. */
	protected abstract IllegalStateException illegal();

	@Override
	public FileSystem getFileSystem() {
		throw illegal();
	}

	@Override
	public boolean isAbsolute() {
		throw illegal();
	}

	@Override
	public Path getRoot() {
		throw illegal();
	}

	@Override
	public Path getFileName() {
		throw illegal();
	}

	@Override
	public Path getParent() {
		throw illegal();
	}

	@Override
	public int getNameCount() {
		throw illegal();
	}

	@Override
	public Path getName(int index) {
		throw illegal();
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		throw illegal();
	}

	@Override
	public boolean startsWith(Path other) {
		throw illegal();
	}

	@Override
	public boolean startsWith(String other) {
		throw illegal();
	}

	@Override
	public boolean endsWith(Path other) {
		throw illegal();
	}

	@Override
	public boolean endsWith(String other) {
		throw illegal();
	}

	@Override
	public Path normalize() {
		throw illegal();
	}

	@Override
	public Path resolve(Path other) {
		throw illegal();
	}

	@Override
	public Path resolve(String other) {
		throw illegal();
	}

	@Override
	public Path resolveSibling(Path other) {
		throw illegal();
	}

	@Override
	public Path resolveSibling(String other) {
		throw illegal();
	}

	@Override
	public Path relativize(Path other) {
		throw illegal();
	}

	@Override
	public URI toUri() {
		throw illegal();
	}

	@Override
	public Path toAbsolutePath() {
		throw illegal();
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		throw illegal();
	}

	@Override
	public File toFile() {
		throw illegal();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw illegal();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		throw illegal();
	}

	@Override
	public Iterator<Path> iterator() {
		throw illegal();
	}

	@Override
	public int compareTo(Path other) {
		throw illegal();
	}
}
