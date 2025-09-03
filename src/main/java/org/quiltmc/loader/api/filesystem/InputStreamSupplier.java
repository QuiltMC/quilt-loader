/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.loader.api.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.function.Supplier;

import org.quiltmc.loader.impl.filesystem.InputStreamToByteChannel;

/** Essentially a {@link Supplier} for an {@link InputStream}, but which may throw an {@link IOException} during
 * operations, and may provide more optimised versions of {@link #computeLength()} and {@link #createByteChannel()}.
 * <p>
 * A simple byte array based version is available in {@link ByteArrayInputStreamSupplier} */
@FunctionalInterface
public interface InputStreamSupplier {

	/** Attempts to obtain an {@link InputStream}.
	 * 
	 * @throws IOException if something goes wrong while creating the {@link InputStream}. */
	InputStream get() throws IOException;

	/** Attempts to obtain the length of the {@link InputStream}. This is used to implement
	 * {@link Files#size(java.nio.file.Path)}.
	 * <p>
	 * The default implementation obtains the {@link InputStream} from {@link #get()}, and reads all bytes from it to
	 * accurately compute the size - you will most likely be able to compute this more optimally. */
	default long computeLength() throws IOException {
		byte[] buffer = new byte[4096];
		long size = 0;

		try (InputStream stream = get()) {
			int read;
			while ((read = stream.read(buffer)) > 0) {
				size += read;
			}
		}

		return size;
	}

	/** Java's file systems allow opening any file as a {@link SeekableByteChannel}, which requires that quilt maintain
	 * a complete buffer of all read bytes so far - and you may be able to provide a more optimised implementation of
	 * this. The returned byte channel should not support any modification operations.
	 * 
	 * @throws IOException if something goes wrong while creating the {@link SeekableByteChannel} */
	default SeekableByteChannel createByteChannel() throws IOException {
		return new InputStreamToByteChannel(get());
	}
}
