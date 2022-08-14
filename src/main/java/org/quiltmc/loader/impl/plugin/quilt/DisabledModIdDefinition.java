package org.quiltmc.loader.impl.plugin.quilt;


import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.discovery.ModCandidate;

/** A concrete definition that mandates that the modid must <strong>not</strong> be loaded by the given singular {@link ModCandidate}, and no
 * others. (The resolver pre-validates that we don't have duplicate mandatory mods, so this is always valid by the time
 * this is used). */
public final class DisabledModIdDefinition extends ModIdDefinition {
	final ModLoadOption option;

	public DisabledModIdDefinition(ModLoadOption candidate) {
		this.option = candidate;
	}

	@Override
	String getModId() {
		return option.id();
	}

	@Override
	ModLoadOption[] sources() {
		return new ModLoadOption[] { option };
	}

	@Override
	public void define(RuleDefiner definer) {
		definer.atMost(0, option);
	}

	@Override
	public boolean onLoadOptionAdded(LoadOption option) {
		return false;
	}

	@Override
	public boolean onLoadOptionRemoved(LoadOption option) {
		return false;
	}

	@Override
	String getFriendlyName() {
		return option.metadata().name() + " (" + option.id() + ")";
	}

	@Override
	public String toString() {
		return "disabled " + option.fullString();
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Disabled mod ");
		errors.append(getFriendlyName());
		errors.append(" v");
		errors.append(option.metadata().version());
	}
}
