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
