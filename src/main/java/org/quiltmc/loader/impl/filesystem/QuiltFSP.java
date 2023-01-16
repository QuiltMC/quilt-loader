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
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

class QuiltFSP<FS extends QuiltBaseFileSystem<FS, ?>> {

	private final String scheme;
	private final Map<String, WeakReference<FS>> activeFilesystems = new HashMap<>();

	static {
		// Java requires we create a class named "Handler"
		// in the package "<system-prop>.<scheme>"
		// See java.net.URL#URL(java.lang.String, java.lang.String, int, java.lang.String)
		final String key = "java.protocol.handler.pkgs";
		final String pkg = "org.quiltmc.loader.impl.filesystem";
		String prop = System.getProperty(key);
		if (prop == null) {
			System.setProperty(key, pkg);
		} else if (!prop.contains(pkg)) {
			System.setProperty(key, prop + "|" + pkg);
		}
	}

	QuiltFSP(String scheme) {
		this.scheme = scheme;
	}

	synchronized void register(FS fs) {
		URI uri = URI.create(scheme + "://" + fs.name + "/hello");
		if (!"/hello".equals(uri.getPath())) {
			throw new IllegalArgumentException("Not a valid name - it includes a path! '" + fs.name + "'");
		}
		WeakReference<FS> oldRef = activeFilesystems.get(fs.name);
		if (oldRef != null) {
			FS old = oldRef.get();
			if (old != null) {
				throw new IllegalStateException("Multiple registered file systems for name '" + fs.name + "'");
			}
		}
		activeFilesystems.put(fs.name, new WeakReference<>(fs));
	}

	@Nullable
	synchronized FS getFileSystem(String name) {
		WeakReference<FS> ref = activeFilesystems.get(name);
		return ref != null ? ref.get() : null;
	}

	synchronized FS getFileSystem(URI uri) {
		if (!scheme.equals(uri.getScheme())) {
			throw new IllegalArgumentException("Wrong scheme! " + uri);
		}
		String authority = uri.getAuthority();
		if (authority == null) {
			authority = uri.getHost();
		} else if (authority.endsWith(":0")) {
			// We add a (useless) port to allow URI.authority and host to be populated
			authority = authority.substring(0, authority.length() - 2);
		}
		FS fs = getFileSystem(authority);
		if (fs == null) {
			throw new IllegalArgumentException("Unknown file system name '" + authority + "'");
		}
		return fs;
	}

	synchronized void closeFileSystem(FS fs) {
		WeakReference<FS> removedRef = activeFilesystems.remove(fs.name);
		if (removedRef.get() != fs) {
			throw new IllegalStateException("FileSystem already removed!");
		}
	}

	public Map<String, Object> readAttributes(FileSystemProvider from, Path path, String attributes,
		LinkOption[] options) throws IOException {

		Map<String, Object> map = new HashMap<>();
		BasicFileAttributes attrs = from.readAttributes(path, BasicFileAttributes.class, options);
		map.put("isDirectory", attrs.isDirectory());
		map.put("isRegularFile", attrs.isRegularFile());
		map.put("isSymbolicLink", attrs.isSymbolicLink());
		map.put("isOther", attrs.isOther());
		map.put("size", attrs.size());
		map.put("fileKey", attrs.fileKey());
		map.put("lastModifiedTime", attrs.lastModifiedTime());
		map.put("lastAccessTime", attrs.lastAccessTime());
		map.put("creationTime", attrs.creationTime());
		return map;
	}
}
