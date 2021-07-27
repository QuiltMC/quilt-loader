package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

import net.fabricmc.loader.api.metadata.ModDependency;

/** A {@link ModLink} that implements fabric.mod.json "break" clause
 *  @deprecated since we wrap fabric mod metadata in a quilt wrapper, so this shouldn't be used anymore. */
@Deprecated
final class FabricModBreakLink extends ModLink {
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

	public FabricModBreakLink(Logger logger, ModLoadOption source, ModDependency publicDep, ModIdDefinition with) {
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
	FabricModBreakLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {
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
		FabricModBreakLink other = (FabricModBreakLink) o;

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

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {

		errors.append(this.invalidOptions.isEmpty() ? "-" : "x");
		errors.append(" Mod ").append(ModSolver.getLoadOptionDescription(this.source))
				.append(" conflicts with ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
				.append(" of ");

		ModIdDefinition def = this.with;
		ModLoadOption[] sources = def.sources();

		if (sources.length == 0) {
			errors.append("unknown mod '").append(def.getModId()).append("'\n")
					.append("\t- You must remove ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
					.append(" of '").append(def.getModId()).append("'.");
		} else {
			errors.append(def.getFriendlyName());

			if (this.invalidOptions.isEmpty()) {
				errors.append("\n\t- You must remove ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
						.append(" of ").append(def.getFriendlyName()).append('.');
			}

			if (sources.length == 1) {
				errors.append("\n\t- Your current version of ").append(ModSolver.getCandidateName(sources[0].candidate))
					.append(" is ").append(ModSolver.getCandidateFriendlyVersion(sources[0].candidate)).append(".");
			} else {
				errors.append("\n\t- You have the following versions available:");

				for (ModLoadOption source : sources) {
					errors.append("\n\t\t- ").append(ModSolver.getCandidateFriendlyVersion(source)).append(".");
				}
			}
		}
	}
}
