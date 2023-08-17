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

package org.quiltmc.loader.api;

/** Options for {@link ExtendedFiles#mount(java.nio.file.Path, java.nio.file.Path, MountOption...)} */
public enum MountOption {

	/** Replace an existing file if it exists when mounting. This cannot replace a non-empty directory. */
	REPLACE_EXISTING,

	/** Indicates that the mounted file will not permit writes.
	 * <p>
	 * This option is incompatible with {@link #COPY_ON_WRITE} */
	READ_ONLY,

	/** Indicates that the mounted file will copy to a new, separate file when written to.
	 * <p>
	 * This option is incompatible with {@link #READ_ONLY} */
	COPY_ON_WRITE,
}
