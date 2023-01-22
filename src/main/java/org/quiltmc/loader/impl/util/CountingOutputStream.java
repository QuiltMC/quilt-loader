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
import java.io.OutputStream;

public class CountingOutputStream extends OutputStream {

	private final OutputStream to;
	private int count;

	public CountingOutputStream(OutputStream to) {
		this.to = to;
	}

	@Override
	public void write(int b) throws IOException {
		to.write(b);
		count++;
	}

	@Override
	public void write(byte[] b) throws IOException {
		to.write(b);
		count += b.length;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		to.write(b, off, len);
		count += len;
	}

	@Override
	public void close() throws IOException {
		to.close();
	}

	@Override
	public void flush() throws IOException {
		to.flush();
	}

	public void resetBytesWritten() {
		count = 0;
	}

	public int getBytesWritten() {
		return count;
	}
}
