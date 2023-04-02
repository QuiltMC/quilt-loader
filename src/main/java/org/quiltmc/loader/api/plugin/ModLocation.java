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

package org.quiltmc.loader.api.plugin;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Information about where a mod came from. Passed into the various {@link QuiltLoaderPlugin} scan methods. */
@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface ModLocation {

	/** @return True if the mod is directly on the classpath, otherwise false. */
	boolean onClasspath();

	/** @return True if the mod was directly scanned as a file or folder, rather than being scanned as a sub-mod.
	 *         This also returns true if {@link #onClasspath()} returns true.
	 *         Influences {@link ModLoadOption#isMandatory()} in the built-in plugins. */
	boolean isDirect();
}
