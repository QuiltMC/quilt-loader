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

package org.quiltmc.loader.impl.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class FileUtil {

	// TODO: Move to multi-release jars, and define a java 9 version of this class!

	/** Reads all bytes from the given {@link InputStream}. On java 9 and above this calls the "readAllBytes()" method
	 * in InputStream. */
	public static byte[] readAllBytes(InputStream from) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];

		while (true) {
			int read = from.read(buffer);
			if (read <= 0) {
				break;
			}
			baos.write(buffer, 0, read);
		}

		return baos.toByteArray();
	}
}
