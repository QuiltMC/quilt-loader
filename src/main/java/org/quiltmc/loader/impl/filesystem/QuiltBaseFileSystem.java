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

import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Collections;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

public abstract class QuiltBaseFileSystem
<
	FS extends QuiltBaseFileSystem<FS, P>,
	P extends QuiltBasePath<FS, P>
>
extends FileSystem
{
	static {
		DelegatingUrlStreamHandlerFactory.load();
	}

	final Class<FS> filesystemClass;
	final Class<P> pathClass;

	final String name;
	final P root;

	QuiltBaseFileSystem(Class<FS> filesystemClass, Class<P> pathClass, String name) {
		this.filesystemClass = filesystemClass;
		this.pathClass = pathClass;

		this.name = name;
		this.root = createPath(null, QuiltBasePath.NAME_ROOT);
	}

	abstract P createPath(@Nullable P parent, String name);

	public String getName() {
		return name;
	}

	public Path getRoot() {
		return root;
	}

	@Override
	public String getSeparator() {
		return QuiltBasePath.NAME_ROOT;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Collections.singleton(root);
	}

	void checkOpen() throws ClosedFileSystemException {
		if (!isOpen()) {
			throw new ClosedFileSystemException();
		}
	}

	@Override
	public Path getPath(String first, String... more) {
		if (first.isEmpty()) {
			return createPath(null, "");
		}

		if (more.length == 0) {
			P path = first.startsWith("/") ? root : null;
			for (String sub : first.split("/")) {
				if (path == null) {
					path = createPath(null, sub);
				} else {
					path = path.resolve(sub);
				}
			}
			return path;
		} else {
			P path = createPath(null, first);
			for (String sub : more) {
				path = path.resolve(sub);
			}
			return path;
		}
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		if (syntaxAndPattern.startsWith("regex:")) {
			Pattern pattern = Pattern.compile(syntaxAndPattern.substring("regex:".length()));
			return path -> pattern.matcher(path.toString()).matches();
		} else if (syntaxAndPattern.startsWith("glob:")) {
			throw new AbstractMethodError("// TODO: Implement glob syntax matching!");
		} else {
			throw new UnsupportedOperationException("Unsupported syntax or pattern: '" + syntaxAndPattern + "'");
		}
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}
}
