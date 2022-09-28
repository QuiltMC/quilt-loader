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

package org.quiltmc.loader.impl.plugin;

import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

/** Indicates the current state in the current cycle. */
enum PerCycleStep {

	/** Indicates that the cycle has only just started. */
	START,

	/** Indicates that we've called {@link BuiltinQuiltPlugin#beforeSolve()}, and now we're solving. */
	SOLVE,

	/** Indicates that solving has finished, and now we're resolving {@link TentativeLoadOption}s. */
	POST_SOLVE_TENTATIVE,

	/** Indicates that solving was successful, and we have a resulting {@link ModSolveResult} to end loading with. */
	SUCCESS;
}
