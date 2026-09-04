/*
 * Copyright 2023 QuiltMC
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
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.quiltmc.loader.api.ExtendedFiles;

/** {@link Inflater} based decompressor for
 * {@link ExtendedFiles#mountSubFile(java.nio.file.Path, long, int, IOFunction, int, java.nio.file.Path, org.quiltmc.loader.api.MountOption...)} */
public final class StandardZipDecompressor implements IOFunction<InputStream, InputStream> {

	public static final StandardZipDecompressor INSTANCE = new StandardZipDecompressor();

	private StandardZipDecompressor() {}

	@Override
	public InputStream apply(InputStream input) throws IOException {
		return new InflaterInputStream(input, new Inflater(true));
	}

	@Override
	public String toString() {
		return "StandardZipDecompressor";
	}
}
