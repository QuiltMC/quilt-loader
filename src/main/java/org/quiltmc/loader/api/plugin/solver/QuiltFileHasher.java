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

package org.quiltmc.loader.api.plugin.solver;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A file hash helper, which caches previously computed hashes for performance. */
@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface QuiltFileHasher {

	/** @return The length of the byte arrays that all hash related functions return. This is guaranteed to be at least 4. */
	int getHashLength();

	/** Computes the hash of the given path, suitable for {@link ModLoadOption#computeOriginHash(QuiltFileHasher)}.
	 * <p>
	 * The only guarantee is that, if the given path is a file (or contained within a file via
	 * {@link QuiltPluginManager#getParent(Path)}) this will return a value that is stable over multiple launches on the
	 * same computer, if the original file is unchanged and the version of quilt-loader remains the same.
	 * <p>
	 * If the given path refers to a folder on the {@link FileSystems#getDefault() default file system} then this will
	 * be based on the name of the path as given, rather than the contents of the folder. (Use
	 * {@link #computeRecursiveHash(Path)} if you need the hash to depend on it's contents).
	 * 
	 * @return a byte array of length {@link #getHashLength()}. */
	byte[] computeNormalHash(Path path) throws IOException;

	/** Computes the hash of the given folder, suitable for {@link ModLoadOption#computeOriginHash(QuiltFileHasher)}.
	 * <p>
	 * If the given path is not a folder that this behaves identically to {@link #computeNormalHash(Path)}. Otherwise
	 * this returns a hash that is dependent on the contents of the folder. This will never cache the hash if the path
	 * is a folder on the default file system. No guarantees are made about the hash chosen, other than that it will be
	 * stable between launches on the same computer when using the same version of quilt-loader.
	 * 
	 * @return a byte array of length {@link #getHashLength()}. */
	byte[] computeRecursiveHash(Path folder) throws IOException;
}
