package org.quiltmc.loader.api.plugin.solver;

import java.util.Collection;

public interface ModSolveResult {

	// TODO: add mod map!

	<O extends LoadOption> SpecificLoadOptionResult<O> getResult(Class<O> clazz);

	public interface SpecificLoadOptionResult<O extends LoadOption> {
		Collection<O> getOptions();

		boolean isPresent(O option);
	}
}
