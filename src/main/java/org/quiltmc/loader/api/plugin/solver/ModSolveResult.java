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

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface ModSolveResult {

	/** @return Every mod, not including mods provided by other mods. */
	Map<String, ModLoadOption> directMods();

	/** @return Every mod that is provided by another mod. */
	Map<String, ModLoadOption> providedMods();

	<O> SpecificLoadOptionResult<O> getResult(Class<O> clazz);

	/** @return A map of all found files which were not able to be loaded, mapped to the reason why they were unable to
	 *         be loaded. This only contains paths on the {@link FileSystems#getDefault() default filesystem} */
	Map<Path, String> getUnknownFiles();

	/** @return A map of all found paths which were not able to be loaded, mapped to the reason why they were unable to
	 *         be loaded. This doesn't contain any {@link Path}s in {@link #getUnknownFiles()} */
	Map<String, String> getIrregularUnknownFiles();

	public interface SpecificLoadOptionResult<O> {
		Collection<O> getOptions();

		boolean isPresent(O option);
	}
}
