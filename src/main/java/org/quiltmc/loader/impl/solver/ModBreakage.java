package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

import net.fabricmc.loader.api.metadata.ModDependency;

final class ModBreakage extends ModLink {
	final ModLoadOption source;
	final ModDependency publicDep;
	final ModIdDefinition with;

	/** Every mod option that this does NOT conflict with - as such it can be loaded at the same time as {@link #source}. */
	final List<ModLoadOption> validOptions;

	/** Every mod option that this DOES conflict with - as such it must not be loaded at the same time as
	 * {@link #source}. */
	final List<ModLoadOption> invalidOptions;

	/** Every option. (This is just {@link #validOptions} plus {@link #invalidOptions} */
	final List<ModLoadOption> allOptions;

	public ModBreakage(Logger logger, ModLoadOption source, ModDependency publicDep, ModIdDefinition with) {
		this.source = source;
		this.publicDep = publicDep;
		this.with = with;
		validOptions = new ArrayList<>();
		invalidOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			logger.info("[ModSolver] Adding a mod breakage:");
			logger.info("[ModSolver]   from " + source.fullString());
			logger.info("[ModSolver]   with " + with.getModId());
		}

		for (ModLoadOption option : with.sources()) {
			allOptions.add(option);

			if (publicDep.matches(option.candidate.getInfo().getVersion())) {
				invalidOptions.add(option);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  +  breaking option: " + option.fullString());
				}
			} else {
				validOptions.add(option);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  x  non-conflicting option: " + option.fullString());
				}
			}
		}
	}

	@Override
	ModBreakage put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
		LoadOption[] disallowed = new LoadOption[invalidOptions.size()];
		for (int i = 0; i < invalidOptions.size(); i++) {
			// We don't want to have multiple "variables" for a single mod, since a provided mod is always present
			// if the providing mod is present and vice versa. Calling getRoot means we depend on the provider and
			// therefore solve properly rather than potentially saying the provided mod is present but the provide
			// isn't and vice versa.
			disallowed[i] = invalidOptions.get(i).getRoot();
		}
		helper.halfOr(this, new NegatedLoadOption(source), disallowed);
		return this;
	}

	@Override
	public String toString() {
		return source + " breaks " + with + " version " + publicDep;
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
		ModBreakage other = (ModBreakage) o;

		if (validOptions.isEmpty() != other.validOptions.isEmpty()) {
			return validOptions.isEmpty() ? -1 : 1;
		}

		int c = source.candidate.getOriginUrl().toString()
			.compareTo(other.source.candidate.getOriginUrl().toString());

		if (c != 0) {
			return c;
		}

		return with.compareTo(other.with);
	}
}
