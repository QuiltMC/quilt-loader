package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

class QuiltModLinkDepOnly extends QuiltModLinkDep {
	final Logger logger;

	final ModDependency.Only publicDep;
	final List<ModLoadOption> validOptions;
	final List<ModLoadOption> invalidOptions;
	final List<ModLoadOption> allOptions;

	final QuiltModLinkDep unless;

	public QuiltModLinkDepOnly(Logger logger, RuleContext ctx, LoadOption source, ModDependency.Only publicDep) {
		super(source);
		this.logger = logger;
		this.publicDep = publicDep;
		validOptions = new ArrayList<>();
		invalidOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			logger.info("[ModSolver] Adding a mod depencency from " + source + " to " + publicDep.id().id());
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
				validOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					logger.info("[ModSolver]  +  valid option: " + mod.fullString());
				}
			} else {
				invalidOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					String reason = groupMatches ? "mismatched group" : "wrong version";
					logger.info("[ModSolver]  x  mismatching option: " + mod.fullString() + " because " + reason);
				}
			}

		}
		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		boolean changed = validOptions.remove(option);
		changed |= invalidOptions.remove(option);
		allOptions.remove(option);
		return changed;
	}

	@Override
	void define(RuleDefiner definer) {

		boolean optional = publicDep.optional();
		List<ModLoadOption> options = optional ? invalidOptions : validOptions;

		if (optional && options.isEmpty()) {
			return;
		}

		LoadOption[] array = new LoadOption[options.size() + (unless == null ? 1 : 2)];
		int i = 0;

		for (; i < options.size(); i++) {
			array[i] = options.get(i);
			if (optional) {
				array[i] = definer.negate(array[i]);
			}
		}

		// i is incremented when we exit the for loop, so this is fine.
		array[i++] = definer.negate(source);
		if (unless != null) {
			array[i] = unless.source;
		}

		definer.atLeastOneOf(array);
	}

	@Override
	boolean hasAnyValidOptions() {
		return !validOptions.isEmpty();
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

		errors.append("x Dependency for " +  source + ":\n");
		errors.append("\t-");

		errors.append("x Mod /* TODO: Fetch the Mod ID */ depends on /* FIXME: Implement this!*/");

		/*
		errors.append(this.validOptions.isEmpty() ? "x" : "-");
		errors.append(" Mod ").append(ModSolver.getLoadOptionDescription(this.source))
				.append(" requires ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
				.append(" of ");
		ModIdDefinition def = this.on;
		ModLoadOption[] sources = def.sources();
	
		if (sources.length == 0) {
			errors.append("unknown mod '").append(def.getModId()).append("'\n")
					.append("\t- You must install ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
					.append(" of '").append(def.getModId()).append("'.");
		} else {
			errors.append(def.getFriendlyName());
	
			if (this.validOptions.isEmpty()) {
				errors.append("\n\t- You must install ").append(ModSolver.getDependencyVersionRequirements(this.publicDep))
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
		*/
	}
}
