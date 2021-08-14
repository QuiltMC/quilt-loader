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

		errors.append("x Break for " + source + ":\n");
		errors.append("\t-");

		errors.append("x Mod /* TODO: Fetch the Mod ID */ depends on /* FIXME: Implement this!*/");

		/* errors.append(this.validOptions.isEmpty() ? "x" : "-");
		 * errors.append(" Mod ").append(ModSolver.getLoadOptionDescription(this.source))
		 * .append(" requires ").append(ModSolver.getDependencyVersionRequirements(this.publicDep)) .append(" of ");
		 * ModIdDefinition def = this.on; ModLoadOption[] sources = def.sources(); if (sources.length == 0) {
		 * errors.append("unknown mod '").append(def.getModId()).append("'\n")
		 * .append("\t- You must install ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
		 * .append(" of '").append(def.getModId()).append("'."); } else { errors.append(def.getFriendlyName()); if
		 * (this.validOptions.isEmpty()) {
		 * errors.append("\n\t- You must install ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
		 * .append(" of ").append(def.getFriendlyName()).append('.'); } if (sources.length == 1) {
		 * errors.append("\n\t- Your current version of ").append(ModSolver.getCandidateName(sources[0].candidate))
		 * .append(" is ").append(ModSolver.getCandidateFriendlyVersion(sources[0].candidate)).append("."); } else {
		 * errors.append("\n\t- You have the following versions available:"); for (ModLoadOption source : sources) {
		 * errors.append("\n\t\t- ").append(ModSolver.getCandidateFriendlyVersion(source)).append("."); } } } */
	}
}
