package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;

class QuiltModLinkBreakOnly extends QuiltModLinkBreak {
	final Logger logger;

	final ModDependency.Only publicDep;
	final List<ModLoadOption> conflictingOptions;
	final List<ModLoadOption> okayOptions;
	final List<ModLoadOption> allOptions;

	final QuiltModLinkDep unless;

	public QuiltModLinkBreakOnly(Logger logger, RuleContext ctx, LoadOption source, ModDependency.Only publicDep) {
		super(source);
		this.logger = logger;
		this.publicDep = publicDep;
		conflictingOptions = new ArrayList<>();
		okayOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			logger.info("[ModSolver] Adding a mod break from " + source + " to " + publicDep.id().id());
		}

		ModDependency except = publicDep.unless();
		if (except != null && !except.shouldIgnore()) {
			QuiltModDepOption option = new QuiltModDepOption(except);
			ctx.addOption(option);
			this.unless = ModSolver.createModDepLink(logger, ctx, option, except);
			ctx.addRule(unless);
		} else {
			this.unless = null;
		}
	}

	@Override
	boolean onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;

			if (!mod.modId().equals(publicDep.id().id())) {
				return false;
			}

			allOptions.add(mod);

			String maven = publicDep.id().mavenGroup();
			boolean groupMatches = maven.isEmpty() || maven.equals(mod.group());

			if (groupMatches && publicDep.matches(mod.version())) {
				conflictingOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  x  conflicting option: " + mod.fullString());
				}
				return true;
			} else {
				okayOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					String reason = groupMatches ? "different group" : "different version";
					logger.info("[ModSolver]  +  okay option: " + mod.fullString() + " because " + reason);
				}
			}

		}
		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		boolean changed = conflictingOptions.remove(option);
		changed |= okayOptions.remove(option);
		allOptions.remove(option);
		return changed;
	}

	@Override
	void define(RuleDefiner definer) {

		// "optional" is meaningless for breaks
		List<ModLoadOption> conficts = conflictingOptions;

		if (conficts.isEmpty()) {
			return;
		}

		LoadOption[] options = new LoadOption[unless == null ? 2 : 3];
		options[1] = definer.negate(source);

		if (unless != null) {
			options[2] = definer.negate(unless.source);
		}

		for (ModLoadOption conflict : conficts) {
			options[0] = definer.negate(conflict);
			definer.atLeastOneOf(options);
		}
	}

	@Override
	boolean hasAnyConflictingOptions() {
		return !conflictingOptions.isEmpty();
	}

	@Override
	public String toString() {
		return publicDep.toString();
	}

	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.singleton(source);
	}

	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return allOptions;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {

		errors.append("Breakage for ");

		errors.append(source);
		errors.append(" on ");
		errors.append(publicDep.id());
		errors.append(" versions ");
		errors.append(publicDep.versions());
		errors.append(" (");
		errors.append(conflictingOptions.size());
		errors.append(" breaking options, ");
		errors.append(okayOptions.size());
		errors.append(" okay options)");

		for (ModLoadOption option : conflictingOptions) {
			errors.append("\n\tx " + option.fullString());
		}

		for (ModLoadOption option : okayOptions) {
			errors.append("\n\t+ " + option.fullString());
		}
	}
}
