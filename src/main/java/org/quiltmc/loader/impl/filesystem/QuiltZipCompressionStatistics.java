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

import java.nio.file.Path;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Optional statistics gatherer for {@link QuiltZipFileSystem#writeQuiltCompressedFileSystem} */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public interface QuiltZipCompressionStatistics {

	/** Called from any thread when a file is stored directly inside the output file.
	 * 
	 * @param rawSize the size of the file, before compression
	 * @param storedSize the size of the file after compression.
	 * @param isCompressed if the file was actually compressed. */
	void onStoreInternal(Path file, int rawSize, int storedSize, boolean isCompressed);

	/** Called from any thread when a file is stored as a reference to an existing file.
	 *
	 * @param file The file inside the filesystem that was stored.
	 * @param external The external reference file that the file will point to.
	 * @param rawSize the size of the file, before compression
	 * @param storedSize the size of the file after compression.
	 * @param isCompressed if the file was actually compressed. */
	void onStoreReference(Path file, Path external, int rawSize, int storedSize, boolean isCompressed);

	void finish(long totalSize);
}
