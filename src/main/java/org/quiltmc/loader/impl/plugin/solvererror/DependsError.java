package org.quiltmc.loader.impl.plugin.solvererror;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.SolverErrorReportContext;
import org.quiltmc.loader.impl.plugin.VersionRangeDescriber;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;

/**
 * Reports on a set of rules that indicate an issue with a required dependency.
 * This can either be that it is missing, or that it is unable to load do to another error.
 */
public class DependsError extends SolverError {
	final Set<ModLoadOption> from = new LinkedHashSet<>();
	final QuiltRuleDepOnly depends;

	public DependsError(ModLoadOption from, QuiltRuleDepOnly depends) {
		this.from.add(from);
		this.depends = depends;
	}

	@Override
	public boolean mergeInto(SolverError into) {
		if (into instanceof DependsError) {
			DependsError depDst = (DependsError) into;
			if (!depends.publicDep.id().equals(depDst.depends.publicDep.id()) || !depends.publicDep.versionRange().equals(depDst.depends.publicDep.versionRange())) {
				return false;
			}
			depDst.from.addAll(from);
			return true;
		}
		return false;
	}

	@Override
	public boolean isRelatedOption(LoadOption option) {
		return from.contains(option) || this.depends.getValidOptions().contains(option);
	}

	@Override
	public void report(SolverErrorReportContext context) {

		// Title:
		// "BuildCraft" requires [version 1.5.1] of "Quilt Standard Libraries", which is
		// missing!

		// Description:
		// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
		ModLoadOption mandatoryMod = from.iterator().next();
		String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).map(name -> "'" + name + "'").collect(Collectors.joining(", ")) + "]" : ("'" + mandatoryMod.metadata().name() + "'");

		VersionRange range = depends.publicDep.versionRange();
		String depName = depends.publicDep.id().id();
		QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, range, depName, true, false);

		Object[] secondData = new Object[(depends.getWrongOptions().size() == 1 || depends.getValidOptions().size() == 1) ? 1 : 0];
		String secondKey = "error.dep.";
		if (depends.getAllOptions().isEmpty()) {
			secondKey += "missing";
		} else if (depends.getWrongOptions().size() > 1) {
			secondKey += "multi_mismatch";
		} else if (depends.getWrongOptions().size() == 1) {
			secondKey += "single_mismatch";
			secondData[0] = depends.getWrongOptions().iterator().next().version().toString();
		} else if (depends.getValidOptions().size() > 1) {
			secondKey += "multi_valid";
		} else {
			secondKey += "single_valid";
			secondData[0] = depends.getValidOptions().iterator().next().version().toString();
		}
		QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
		QuiltLoaderText title = QuiltLoaderText.translate("error.dep.join.title", first, second);
		QuiltDisplayedError error = context.createError(title);

		setIconFromMod(error, mandatoryMod);
		addFiles(error, context, "error.dep.requiring", from);
		addFiles(error, context, "error.dep.valid", depends.getValidOptions());
		addFiles(error, context, "error.dep.invalid", depends.getWrongOptions());

		addIssueLink(error, mandatoryMod);

		StringBuilder report = new StringBuilder("Failed to load ")
				.append(rootModName)
				.append(" because ")
				.append(from.size() > 1 ? "they" : "it")
				.append(" needs");
		if (!depends.publicDep.versionRange().equals(VersionRange.ANY)) {
			addVersionString(report, depends.publicDep, false, false);
		} else {
			report.append(" ");
		}
		report.append("'");
		String depModName = depends.getAllOptions()
				.stream()
				.map(ModLoadOption::metadata)
				.map(ModMetadataExt::name)
				.findAny()
				.orElseGet(depends.publicDep.id()::toString);
		report.append(depModName).append("'");
		if (!depends.getValidOptions().isEmpty()) {
			report.append(", which is unable to load due to another error!");
		} else if (depends.getWrongOptions().isEmpty()) {
			report.append(", which is missing!");
		} else {
			if (depends.getWrongOptions().size() == 1) {
				report.append(", and an invalid version is loaded.");
			} else {
				report.append(", and only invalid versions are loaded.");
			}
		}

		error.appendReportText(report.toString(), "");

		if (!depends.publicDep.reason().isEmpty()) {
			error.appendReportText("The reason " + rootModName + " needs '" + depModName + "': " + depends.publicDep.reason());
		}

		error.appendReportText("Mods that need '" + depModName + "': ");
		for (ModLoadOption mod : from) {
			getModReportLine(mod, context, true, false, false).forEach(error::appendReportText);
		}

		if (!depends.getValidOptions().isEmpty()) {
			error.appendReportText("");
			error.appendReportText("Mods unable to load: ");
			for (ModLoadOption mod : depends.getValidOptions()) {
				getModReportLine(mod, context, true, !depends.publicDep.versionRange().equals(VersionRange.ANY), true).forEach(error::appendReportText);
				// TODO: link other errors here.
				if (context.getGraph().isBreaking(mod)) {
					error.appendReportText("  - '" + mod.metadata().name() + "' breaks another mod, see Error " + context.getRelatedErrors(mod, this));
				} else if (context.getGraph().isBroken(mod)) {
					error.appendReportText("  - '" + mod.metadata().name() + "' is broken by another mod, see Error " + context.getRelatedErrors(mod, this));
				} else if (context.getGraph().isProviding(mod)) {
					error.appendReportText("  - '" + mod.metadata().name() + "' provides a duplicated mod, see Error " + context.getRelatedErrors(mod, this));
				} else if (context.getGraph().isDuplicating(mod)) {
					error.appendReportText("  - '" + mod.metadata().name() + "' duplicates another mod, see Error " + context.getRelatedErrors(mod, this));
				}
			}
		}

		if (!depends.getWrongOptions().isEmpty()) {
			error.appendReportText("");
			error.appendReportText("Mods with incorrect versions: ");
			for (ModLoadOption mod : depends.getWrongOptions()) {
				getModReportLine(mod, context, true, false, false).forEach(error::appendReportText);
				if (!depends.publicDep.versionRange().isSatisfiedBy(mod.metadata().version())) {
					StringBuilder version = new StringBuilder();
					this.addVersionRangeString(version, depends.publicDep.versionRange().first());
					error.appendReportText("  - '" + mod.metadata().name() + "' version '" + mod.metadata().version() + "' is not a version" + version);
				}
			}
		}
	}
}
