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

package org.quiltmc.loader.impl.solver;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class ModSolveResultImpl implements ModSolveResult {

	public final Map<String, ModLoadOption> directModMap;
	public final Map<String, ModLoadOption> providedModMap;
	public final Map<Class<?>, LoadOptionResult<?>> extraResults;
	public final Map<Path, String> unknownRegularFiles;
	public final Map<String, String> unknownFiles;

	public ModSolveResultImpl(Map<String, ModLoadOption> directModMap, Map<String, ModLoadOption> providedModMap, //
		Map<Class<?>, LoadOptionResult<?>> extraResults, Map<Path, String> unknownRegularFiles, //
		Map<String, String> unknownFiles) {

		this.directModMap = directModMap;
		this.providedModMap = providedModMap;
		this.extraResults = extraResults;
		this.unknownRegularFiles = unknownRegularFiles;
		this.unknownFiles = unknownFiles;
	}

	@Override
	public Map<String, ModLoadOption> directMods() {
		return directModMap;
	}

	@Override
	public Map<String, ModLoadOption> providedMods() {
		return providedModMap;
	}

	@Override
	public <O> LoadOptionResult<O> getResult(Class<O> optionClass) {
		LoadOptionResult<?> result = extraResults.get(optionClass);
		if (result == null) {
			return new LoadOptionResult<>(Collections.emptyMap());
		}
		return (LoadOptionResult<O>) result;
	}

	@Override
	public Map<Path, String> getUnknownFiles() {
		return unknownRegularFiles;
	}

	@Override
	public Map<String, String> getIrregularUnknownFiles() {
		return unknownFiles;
	}

	public static final class LoadOptionResult<O> implements SpecificLoadOptionResult<O> {
		private final Map<O, Boolean> result;

		public LoadOptionResult(Map<O, Boolean> result) {
			this.result = result;
		}

		@Override
		public Collection<O> getOptions() {
			return result.keySet();
		}

		@Override
		public boolean isPresent(O option) {
			return result.getOrDefault(option, false);
		}
	}
}
