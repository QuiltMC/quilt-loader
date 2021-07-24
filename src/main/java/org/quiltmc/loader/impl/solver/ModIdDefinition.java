package org.quiltmc.loader.impl.solver;

import java.util.Collection;
import java.util.Collections;

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;

/** A concrete definition of a modid. This also maps the modid to the {@link LoadOption} candidates, and so is used
 * instead of {@link LoadOption} in other links. */
abstract class ModIdDefinition extends ModLink {
	abstract String getModId();

	/** @return An array of all the possible {@link LoadOption} instances that can define this modid. May be empty, but
	 *         will never be null. */
	abstract ModLoadOption[] sources();

	/** Utility for {@link #put(DependencyHelper)} which returns only {@link ModLoadOption#getRoot()}
	 * 
	 * @deprecated Since {@link RuleDefiner} should handle this. */
	@Deprecated
	protected static MainModLoadOption[] processSources(ModLoadOption[] array) {
		MainModLoadOption[] dst = new MainModLoadOption[array.length];
		for (int i = 0; i < dst.length; i++) {
			dst[i] = array[i].getRoot();
		}
		return dst;
	}

	abstract String getFriendlyName();

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public boolean isNode() {
		return false;
	}

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.emptySet();
	}

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return Collections.emptySet();
	}
}
