/*
 * Copyright 2016 FabricMC
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

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

final class QuiltFileAttributes implements BasicFileAttributes {

	static final int SIZE_MISSING = -1;
	static final int SIZE_DIRECTORY = -2;

	static final QuiltFileAttributes MISSING = new QuiltFileAttributes(null, SIZE_MISSING);

	private static final FileTime THE_TIME = FileTime.fromMillis(0);

	final Object key;
	final int size;

	public QuiltFileAttributes(Object key, int size) {
		this.key = key == null ? this : key;
		this.size = size;
	}

	@Override
	public FileTime lastModifiedTime() {
		return THE_TIME;
	}

	@Override
	public FileTime lastAccessTime() {
		return THE_TIME;
	}

	@Override
	public FileTime creationTime() {
		return THE_TIME;
	}

	@Override
	public boolean isRegularFile() {
		return size >= 0;
	}

	@Override
	public boolean isDirectory() {
		return size == SIZE_DIRECTORY;
	}

	@Override
	public boolean isSymbolicLink() {
		return false;
	}

	@Override
	public boolean isOther() {
		return false;
	}

	@Override
	public long size() {
		// Non-file sizes are allowed to be unspecified
		// so we just return the magic numbers in that case
		return size;
	}

	@Override
	public Object fileKey() {
		return key;
	}
}
