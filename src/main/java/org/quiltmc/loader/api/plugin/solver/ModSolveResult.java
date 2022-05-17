package org.quiltmc.loader.api.plugin.solver;

import java.util.Collection;
import java.util.Map;

public interface ModSolveResult {

	/** @return Every mod, not including mods provided by other mods. */
	Map<String, ModLoadOption> directMods();

	/** @return Every mod that is provided by another mod. */
	Map<String, ModLoadOption> providedMods();

	<O> SpecificLoadOptionResult<O> getResult(Class<O> clazz);

	public interface SpecificLoadOptionResult<O> {
		Collection<O> getOptions();

		boolean isPresent(O option);
	}
}
