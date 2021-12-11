package org.quiltmc.loader.api.plugin.solver;

import java.util.Collection;

public interface ModSolveResult {

	// TODO: add mod map!

	<O> SpecificLoadOptionResult<O> getResult(Class<O> clazz);

	public interface SpecificLoadOptionResult<O> {
		Collection<O> getOptions();

		boolean isPresent(O option);
	}
}
