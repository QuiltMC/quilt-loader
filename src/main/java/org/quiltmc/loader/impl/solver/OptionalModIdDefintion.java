package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** A concrete definition that allows the modid to be loaded from any of a set of {@link ModCandidate}s. */
final class OptionalModIdDefintion extends ModIdDefinition {
	final String modid;
	final ModLoadOption[] sources;

	public OptionalModIdDefintion(String modid, ModLoadOption[] sources) {
		this.modid = modid;
		this.sources = sources;
	}

	@Override
	String getModId() {
		return modid;
	}

	@Override
	ModLoadOption[] sources() {
		return sources;
	}

	@Override
	String getFriendlyName() {
		String name = null;

		for (ModLoadOption option : sources) {
			String opName = option.candidate.getInfo().getName();

			if (name == null) {
				name = opName;
			} else if (!name.equals(opName)) {
				// TODO!
			}
		}

		return ModSolver.getCandidateName(sources[0]);
	}

	@Override
	OptionalModIdDefintion put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
		helper.atMost(this, 1, processSources(sources));
		return this;
	}

	@Override
	public String toString() {
		switch (sources.length) {
			case 0: return "unknown mod '" + modid + "'";
			case 1: return "optional mod '" + modid + "' (1 source)";
			default: return "optional mod '" + modid + "' (" + sources.length + " sources)";
		}
	}
}
