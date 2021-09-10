package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** A concrete definition that mandates that the modid must be loaded by the given singular {@link ModCandidate}, and no
 * others. (The resolver pre-validates that we don't have duplicate mandatory mods, so this is always valid by the time
 * this is used). */
final class MandatoryModIdDefinition extends ModIdDefinition {
	final MainModLoadOption candidate;

	public MandatoryModIdDefinition(MainModLoadOption candidate) {
		this.candidate = candidate;
	}

	@Override
	String getModId() {
		return candidate.modId();
	}

	@Override
	MainModLoadOption[] sources() {
		return new MainModLoadOption[] { candidate };
	}

	@Override
	void define(RuleDefiner definer) {
		definer.atLeastOneOf(candidate);
	}

	@Override
	boolean onLoadOptionAdded(LoadOption option) {
		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		return false;
	}

	@Override
	String getFriendlyName() {
		return ModSolver.getCandidateName(candidate);
	}

	@Override
	public String toString() {
		return "mandatory " + candidate.fullString();
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Mandatory mod ");
		errors.append(getFriendlyName());
		errors.append(" v");
		errors.append(candidate.candidate.getMetadata().version());
	}
}
