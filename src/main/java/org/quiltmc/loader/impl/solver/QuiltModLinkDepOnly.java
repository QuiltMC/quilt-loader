package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;

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
			return true;
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

		if (publicDep.optional()) {
			errors.append("Optional depencency for ");
		} else {
			errors.append("Depencency for ");
		}

		errors.append(source);
		errors.append(" on ");
		errors.append(publicDep.id());
		errors.append(" versions ");
		errors.append(publicDep.versions());
		errors.append(" (");
		errors.append(validOptions.size());
		errors.append(" valid options, ");
		errors.append(invalidOptions.size());
		errors.append(" invalid options)");

		for (ModLoadOption option : validOptions) {
			errors.append("\n\t+ " + option.fullString());
		}

		for (ModLoadOption option : invalidOptions) {
			errors.append("\n\tx " + option.fullString());
		}
	}
}
