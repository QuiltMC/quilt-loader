package org.quiltmc.loader.impl.solver;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.quiltmc.loader.impl.discovery.ModCandidate;

public final class ModSolveResult {

	/** This doesn't include the provided mods. */
	public final Map<String, ModCandidate> modMap;
	public final Map<String, ModCandidate> providedMap;

	private final Map<Class<? extends LoadOption>, LoadOptionResult<?>> extraResults;

	ModSolveResult(Map<String, ModCandidate> modMap, Map<String, ModCandidate> providedMap, Map<Class<? extends LoadOption>, LoadOptionResult<?>> extraResults) {
		this.modMap = modMap;
		this.providedMap = providedMap;
		this.extraResults = extraResults;
	}

	public <O extends LoadOption> LoadOptionResult<O> getResult(Class<O> optionClass) {
		LoadOptionResult<?> result = extraResults.get(optionClass);
		if (result == null) {
			return new LoadOptionResult<>(Collections.emptyMap());
		}
		return (LoadOptionResult<O>) result;
	}

	public static final class LoadOptionResult<O extends LoadOption> {
		private final Map<O, Boolean> result;

		LoadOptionResult(Map<O, Boolean> result) {
			this.result = result;
		}

		public Collection<O> getOptions() {
			return result.keySet();
		}

		public boolean isPresent(O option) {
			return result.getOrDefault(option, false);
		}
	}
}
