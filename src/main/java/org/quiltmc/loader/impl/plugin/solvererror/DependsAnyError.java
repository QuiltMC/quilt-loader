package org.quiltmc.loader.impl.plugin.solvererror;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.ModDependency;
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
 * Reports on a set of rules that indicate an issue with a required any dependency.
 * It handles each only dep as either missing or unable to load.
 */
public class DependsAnyError extends SolverError {
	final Set<ModLoadOption> from = new LinkedHashSet<>();
	final Set<QuiltRuleDepOnly> depends;

	public DependsAnyError(ModLoadOption from, Set<QuiltRuleDepOnly> dependsAll) {
		this.from.add(from);
		this.depends = dependsAll;
	}

	@Override
	public boolean mergeInto(SolverError into) {
		if (into instanceof DependsAnyError) {
			DependsAnyError depDst = (DependsAnyError) into;
			Set<ModDependency.Only> ours = this.depends.stream().map(rule -> rule.publicDep).collect(Collectors.toSet());
			Set<ModDependency.Only> theirs = depDst.depends.stream().map(rule -> rule.publicDep).collect(Collectors.toSet());

			if (ours.equals(theirs)) {
				depDst.from.addAll(from);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isRelatedOption(LoadOption option) {
		return from.contains(option);
	}

	@Override
	public void report(SolverErrorReportContext context) {

		// Title:
		// "BuildCraft" depends on any of Quilt Standard Libraries and Minecraft!
		// "BuildCraft" depends on any of Quilt Standard Libraries, Minecraft, and Third Mod!

		// Description:
		// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
		ModLoadOption mandatoryMod = from.iterator().next();
		String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

		Iterator<ModDependency.Only> depends = this.depends.stream().map(rule -> rule.publicDep).iterator();
		QuiltLoaderText allMods = QuiltLoaderText.of(depends.next().id().id());
		while (depends.hasNext()) {
			ModDependency.Only next = depends.next();

			if (depends.hasNext()) {
				allMods = QuiltLoaderText.translate("error.dep.join.title", allMods, next.id().id());
			} else {
				allMods = QuiltLoaderText.translate("error.dep.join.last.title", allMods, next.id().id());
			}
		}

		QuiltLoaderText title = QuiltLoaderText.translate("error.dep_any.title", rootModName, allMods);
		QuiltDisplayedError error = context.createError(title);

		setIconFromMod(error, mandatoryMod);

		for (QuiltRuleDepOnly depOnly : this.depends) {
			error.appendDescription(VersionRangeDescriber.describe(depOnly.publicDep.versionRange(), depOnly.publicDep.id().id()));
			if (!depOnly.publicDep.reason().isEmpty()) {
				error.appendDescription(QuiltLoaderText.translate("error.reason.specific", depOnly.publicDep.id().id(), depOnly.publicDep.reason()));
			}

			addFiles(error, context, "error.dep.valid", depOnly.getValidOptions());
			addFiles(error, context, "error.dep.invalid", depOnly.getWrongOptions());
			error.appendDescription(QuiltLoaderText.of(""));
		}

		addFiles(error, context, "error.dep.requiring", from);

		addIssueLink(error, mandatoryMod);

		StringBuilder report = new StringBuilder(rootModName);
		report.append(" depend");
		if (from.size() == 1) {
			report.append("s");
		}
		report.append(" on any of the following mods:");

		boolean skippedBreak = false;

		error.appendReportText(report.toString());
		for (QuiltRuleDepOnly depOnly : this.depends) {
			skippedBreak = false;

			report = new StringBuilder("- ");
			addVersionString(report, depOnly.publicDep, false, true);
			report.append(depOnly.publicDep.id()); // TODO

			if (depOnly.getAllOptions().isEmpty()) {
				report.append(", which is missing!");
				if (depOnly.publicDep.reason().isEmpty()) {
					error.appendReportText(report.toString());
					skippedBreak = true;
					continue;
				}
			} else {
				report.append(":");
			}

			error.appendReportText(report.toString());
			if (!depOnly.publicDep.reason().isEmpty()) {
				error.appendReportText("  Depend reason: " + depOnly.publicDep.reason());
			}

			if (!depOnly.getValidOptions().isEmpty()) {
				error.appendReportText("  Satisfying mods which cannot load: ");
				for (ModLoadOption mod : depOnly.getValidOptions()) {
					error.appendReportText("  " + getModReportLine(mod, context, true, !depOnly.publicDep.versionRange().equals(VersionRange.ANY), false));
				}
			}

			if (!depOnly.getWrongOptions().isEmpty()) {
				error.appendReportText("  Invalid mods: ");
				for (ModLoadOption mod : depOnly.getWrongOptions()) {
					error.appendReportText("  " + getModReportLine(mod, context, true, !depOnly.publicDep.versionRange().equals(VersionRange.ANY), false));
				}
			}
			error.appendReportText("");
		}

		if (skippedBreak) {
			error.appendReportText("");
		}

		error.appendReportText("Requiring mods: ");
		for (ModLoadOption mod : from) {
			getModReportLine(mod, context, true, false, false).forEach(error::appendReportText);
		}
	}
}
