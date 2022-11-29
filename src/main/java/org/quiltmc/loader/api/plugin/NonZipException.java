/*
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.api.plugin;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Thrown by {@link QuiltPluginManager#loadZip(java.nio.file.Path)} if a file couldn't be opened as a zip. */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public class NonZipException extends Exception {

	public NonZipException(String message) {
		super(message);
	}

	public NonZipException(Throwable cause) {
		super(cause);
	}

	public NonZipException(String message, Throwable cause) {
		super(message, cause);
	}

}
