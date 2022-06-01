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

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class QuiltMemoryFolder extends QuiltMemoryEntry {

	private QuiltMemoryFolder(QuiltMemoryPath path) {
		super(path);
	}

	@Override
	protected BasicFileAttributes createAttributes() {
		return new QuiltFileAttributes(path, QuiltFileAttributes.SIZE_DIRECTORY);
	}

	public static final class ReadOnly extends QuiltMemoryFolder {
		final QuiltMemoryPath[] children;

		public ReadOnly(QuiltMemoryPath path, QuiltMemoryPath[] children) {
			super(path);
			this.children = children;
		}
	}

	public static final class ReadWrite extends QuiltMemoryFolder {
		final Set<QuiltMemoryPath> children = Collections.newSetFromMap(new ConcurrentHashMap<>());

		public ReadWrite(QuiltMemoryPath path) {
			super(path);
		}
	}
}
