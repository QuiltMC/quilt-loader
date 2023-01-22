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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import org.quiltmc.loader.api.FasterFiles;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class HashUtil {

	public static byte[] computeHash(Path path) throws IOException {
		if (FasterFiles.isDirectory(path)) {
			// We don't support hash computations here?
			// So instead return the current date & time

			return currentDateAndTimeHash();
		} else {
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-256");

				final byte[] readCache = new byte[0x2000];

				try (InputStream is = Files.newInputStream(path)) {
					int count;
					while ((count = is.read(readCache)) > 0) {
						digest.update(readCache, 0, count);
					}

					return digest.digest();
				}

			} catch (NoSuchAlgorithmException e) {
				throw new IOException(e);
			}
		}
	}

	/** "Hashes" the current date and time. (Except instead of hashing, this just returns the date and time badly
	 * encoded as bytes). */
	public static byte[] currentDateAndTimeHash() {
		LocalDateTime now = LocalDateTime.now();

		int nano = now.getNano();

		return new byte[] { //
			(byte) (now.getYear() & 0xFF), //
			(byte) ((now.getYear() >> 8) & 0xFF), //
			(byte) (now.getMonthValue()), //
			(byte) now.getDayOfMonth(), //
			(byte) now.getHour(), //
			(byte) now.getMinute(), //
			(byte) now.getSecond(), //
			(byte) (nano & 0xFF), //
			(byte) ((nano >> 8) & 0xFF), //
			(byte) ((nano >> 16) & 0xFF), //
			(byte) (nano >> 24)//
		};
	}

	public static String hashToString(byte[] hash) {
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			int i = Byte.toUnsignedInt(b);
			if (i < 0xF) {
				sb.append("0");
			}
			sb.append(Integer.toHexString(i));
			sb.append(" ");
		}
		return sb.toString();
	}
}
