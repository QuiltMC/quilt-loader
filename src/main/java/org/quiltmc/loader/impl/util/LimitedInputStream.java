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

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class LimitedInputStream extends InputStream {
	private final InputStream from;
	private final int limit;

	private int position;

	public LimitedInputStream(InputStream from, int limit) {
		this.from = from;
		this.limit = limit;
	}

	@Override
	public String toString() {
		return "LimitedInputStream { " + position + " / " + limit + " of " + from + " }";
	}

	@Override
	public int available() throws IOException {
		return limit - position;
	}

	@Override
	public int read() throws IOException {
		if (position < limit) {
			position++;
			return from.read();
		} else {
			return -1;
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return 0;
		}
		int max = Math.min(len, limit - position);
		if (max <= 0) {
			return -1;
		}
		// Minecraft 1.18.2 assumes this method always reads as much as possible, rather than just "some bytes"
		int totalRead = 0;
		while (true) {
			int read = from.read(b, off, max);
			if (read <= 0) {
				return totalRead > 0 ? totalRead : read;
			}
			position += read;
			off += read;
			max -= read;
			totalRead += read;
			if (max <= 0) {
				break;
			}
		}
		return totalRead;
	}
}
