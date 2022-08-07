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
import java.net.URI;
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
		this.name = sanitizeName(name);
		this.root = createPath(null, QuiltBasePath.NAME_ROOT);

		// Validate that our sanitising passes this.name through to
		// both the host and authority
		URI uri = root.toUri();
		if (!this.name.equals(uri.getHost())) {
			throw new RuntimeException(this.name + " wasn't found as the host of " + uri);
		}
		if (uri.getAuthority() == null || !uri.getAuthority().contains(this.name)) {
			throw new RuntimeException(this.name + " wasn't found in the authority of " + uri);
		}
	}

	// Shamelessly stolen from UnixUriUtils
	private static final long LOW_MASK = 0x3ff600000000000L;
	private static final long HIGH_MASK = 0x47fffffe07ffffffL;
	private static String sanitizeName(String str) {
		byte[] path = str.getBytes();
		StringBuilder sb = new StringBuilder();

		for(int i = 0; i < path.length; ++i) {
			char c = (char)(path[i] & 255);

			if (matchesMagic(c)) {
				sb.append(c);
			} else if (c == '_') {
				sb.append('-');
			}
			// Since we don't need to decode anything,
			// we just delete characters we don't like!
		}

		return sb.toString();
	}

	private static boolean matchesMagic(char c) {
		if (c < 64) {
			return (1L << c & LOW_MASK) != 0L;
		} else if (c < 128) {
			return (1L << c - 64 & HIGH_MASK) != 0L;
		} else {
			return false;
		}
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
