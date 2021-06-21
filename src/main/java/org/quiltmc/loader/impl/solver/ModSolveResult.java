package org.quiltmc.loader.impl.solver;

import java.util.Map;

import org.quiltmc.loader.impl.discovery.ModCandidate;

public final class ModSolveResult {
	public final Map<String, ModCandidate> modMap;
	public final Map<String, ModCandidate> providedMap;

//	private final Map<Class<?>, >

	public ModSolveResult(Map<String, ModCandidate> modMap, Map<String, ModCandidate> providedMap) {
		this.modMap = modMap;
		this.providedMap = providedMap;
	}
}
