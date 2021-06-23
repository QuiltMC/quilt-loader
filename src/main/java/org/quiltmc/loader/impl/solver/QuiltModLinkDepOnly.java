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
	final ModDependency.Only publicDep;
	final ModIdDefinition on;
	final List<ModLoadOption> validOptions;
	final List<ModLoadOption> invalidOptions;
	final List<ModLoadOption> allOptions;

	final QuiltModLinkDep unless;

	public QuiltModLinkDepOnly(Logger logger, LoadOption source, ModDependency.Only publicDep, Map<String,
		ModIdDefinition> modDefs, DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {

		super(source);
		this.publicDep = publicDep;
		this.on = ModSolver.getOrCreateMod(modDefs, helper, publicDep.id().id());
		validOptions = new ArrayList<>();
		invalidOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			logger.info("[ModSolver] Adding a mod depencency from " + source + " to " + on.getModId());
		}

		for (ModLoadOption option : on.sources()) {
			allOptions.add(option);

			InternalModMetadata oMeta = option.candidate.getMetadata();

			String maven = publicDep.id().mavenGroup();
			boolean groupMatches = maven.isEmpty() || maven.equals(oMeta.group());

			if (groupMatches && publicDep.matches(oMeta.version())) {
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

		ModDependency except = publicDep.unless();
		if (except != null && !except.shouldIgnore()) {
			QuiltModDepOption option = new QuiltModDepOption(except);
			this.unless = ModSolver.createModDepLink(logger, modDefs, helper, option, except);
		} else {
			this.unless = null;
		}
	}

	@Override
	ModLink put(DependencyHelper<LoadOption, ModLink> helper) throws ContradictionException {

		boolean optional = publicDep.optional();
		List<ModLoadOption> options = optional ? invalidOptions : validOptions;

		if (optional && options.isEmpty()) {
			return this;
		}

		LoadOption[] array = new LoadOption[options.size() + (unless == null ? 1 : 2)];
		int i = 0;

		for (; i < options.size(); i++) {
			// We don't want to have multiple "variables" for a single mod, since a provided mod is always present
			// if the providing mod is present and vice versa. Calling getRoot means we depend on the provider and
			// therefore solve properly rather than potentially saying the provided mod is present but the provide
			// isn't and vice versa.
			array[i] = options.get(i).getRoot();
			if (optional) {
				array[i] = new NegatedLoadOption(array[i]);
			}
		}

		// i is incremented when we exit the for loop, so this is fine.
		array[i++] = new NegatedLoadOption(source);
		if (unless != null) {
			array[i] = unless.source;
		}
		helper.clause(this, array);

		if (unless != null) {
			unless.put(helper);
		}

		return this;
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
	protected int compareToSelf(ModLink o) {
		QuiltModLinkDepOnly other = (QuiltModLinkDepOnly) o;

		if (validOptions.isEmpty() != other.validOptions.isEmpty()) {
			return validOptions.isEmpty() ? -1 : 1;
		}

		return on.compareTo(other.on);
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
