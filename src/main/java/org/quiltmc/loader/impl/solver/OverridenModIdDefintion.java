package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** A variant of {@link OptionalModIdDefintion} but which is overridden by a {@link MandatoryModIdDefinition} (and so
 * none of these candidates can load). */
final class OverridenModIdDefintion extends ModIdDefinition {
	final MandatoryModIdDefinition overrider;
	final ModLoadOption[] sources;

	public OverridenModIdDefintion(MandatoryModIdDefinition overrider, ModLoadOption[] sources) {
		this.overrider = overrider;
		this.sources = sources;
	}

	@Override
	String getModId() {
		return overrider.getModId();
	}

	@Override
	ModLoadOption[] sources() {
		return sources;
	}

	@Override
	String getFriendlyName() {
		return overrider.getFriendlyName();
	}

	@Override
	OverridenModIdDefintion put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
		helper.atMost(this, 1, processSources(sources));
		return this;
	}

	@Override
	public String toString() {
		return "overriden mods '" + overrider.getModId() + "' of " + sources.length + " by " + overrider;
	}
}
