/*
 * Copyright 2026 QuiltMC
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
import java.util.Set;
import java.util.TreeSet;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Thrown by {@link ZipMounter#mountZipAt(java.nio.file.Path, java.util.Map, QuiltMapPath, String, ZipHandling)} when a
 * loaded zip file references files which aren't present in the referenced files map. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class MissingFileReferenceException extends IOException {

	private String message = null;

	public final Set<String> missing, validRefs;

	public MissingFileReferenceException(Set<String> missing, Set<String> validRefs) {
		this.missing = missing;
		this.validRefs = validRefs;
	}

	@Override
	public String getMessage() {
		if (message == null) {
			StringBuilder builder = new StringBuilder();
			builder.append("Missing file references with names:\n");
			for (String name : new TreeSet<>(missing)) {
				builder.append("\"");
				builder.append(name);
				builder.append("\"\n");
			}
			builder.append("\nValid references:\n");
			for (String valid : new TreeSet<>(validRefs)) {
				builder.append("\"");
				builder.append(valid);
				builder.append("\"\n");
			}
			String msg = builder.toString();
			message = msg;
			return msg;
		}
		return message;
	}
}
