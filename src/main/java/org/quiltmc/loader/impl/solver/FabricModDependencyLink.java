package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

import net.fabricmc.loader.api.metadata.ModDependency;

/** A {@link ModLink} that implements fabric.mod.json "depends" clause. */
final class FabricModDependencyLink extends ModLink {
	final ModLoadOption source;
	final ModDependency publicDep;
	final ModIdDefinition on;
	final List<ModLoadOption> validOptions;
	final List<ModLoadOption> invalidOptions;
	final List<ModLoadOption> allOptions;

	public FabricModDependencyLink(Logger logger, ModLoadOption source, ModDependency publicDep, ModIdDefinition on) {
		this.source = source;
		this.publicDep = publicDep;
		this.on = on;
		validOptions = new ArrayList<>();
		invalidOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			logger.info("[ModSolver] Adding a mod depencency from " + source + " to " + on.getModId());
			logger.info("[ModSolver]   from " + source.fullString());
		}

		for (ModLoadOption option : on.sources()) {
			allOptions.add(option);

			if (publicDep.matches(option.candidate.getInfo().getVersion())) {
				validOptions.add(option);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  +  valid option: " + option.fullString());
				}
			} else {
				invalidOptions.add(option);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  x  mismatching option: " + option.fullString());
				}
			}
		}
	}

	@Override
	FabricModDependencyLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
		LoadOption[] allowed = new LoadOption[validOptions.size() + 1];
		int i = 0;

		for (; i < validOptions.size(); i++) {
			// We don't want to have multiple "variables" for a single mod, since a provided mod is always present
			// if the providing mod is present and vice versa. Calling getRoot means we depend on the provider and
			// therefore solve properly rather than potentially saying the provided mod is present but the provide
			// isn't and vice versa.
			allowed[i] = validOptions.get(i).getRoot();
		}

		// i is incremented when we exit the for loop, so this is fine.
		allowed[i] = new NegatedLoadOption(source);
		helper.clause(this, allowed);
		return this;
	}

	@Override
	public String toString() {
		return source + " depends on " + on + " version " + publicDep;
	}

	/**
	 * @deprecated Not used yet. In the future this will be used for better error message generation.
	 */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.singleton(source);
	}

	/**
	 * @deprecated Not used yet. In the future this will be used for better error message generation.
	 */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return allOptions;
	}

	@Override
	protected int compareToSelf(ModLink o) {
		FabricModDependencyLink other = (FabricModDependencyLink) o;

		if (validOptions.isEmpty() != other.validOptions.isEmpty()) {
			return validOptions.isEmpty() ? -1 : 1;
		}

		int c = source.candidate.getOriginUrl().toString()
			.compareTo(other.source.candidate.getOriginUrl().toString());

		if (c != 0) {
			return c;
		}

		return on.compareTo(other.on);
	}
}
