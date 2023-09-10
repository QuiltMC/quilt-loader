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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.quiltmc.loader.api.FasterFiles;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class HashUtil {

	public static final int SHA1_HASH_LENGTH = 20;

	public static byte[] computeHash(Path path) throws IOException {
		if (FasterFiles.isDirectory(path)) {
			path = path.toAbsolutePath();
			return computeHash(path.toString());
		} else {
			final byte[] readCache = new byte[0x2000];

			try (InputStream is = Files.newInputStream(path)) {
				MessageDigest digest = createDigest();
				int count;
				while ((count = is.read(readCache)) > 0) {
					digest.update(readCache, 0, count);
				}

				return digest.digest();
			}
		}
	}

	public static byte[] computeHash(String text) {
		return createDigest().digest(text.getBytes(StandardCharsets.UTF_8));
	}

	public static String hashToString(byte[] hash) {
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			int i = Byte.toUnsignedInt(b);
			if (i < 0xF) {
				sb.append("0");
			}
			sb.append(Integer.toHexString(i));
		}
		return sb.toString();
	}

	private static MessageDigest createDigest() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("This JVM doesn't support SHA-1???");
		}
	}

	public static void xorHash(byte[] dst, byte[] src) {
		for (int i = 0; i < dst.length; i++) {
			dst[i] ^= src[i];
		}
	}
}
