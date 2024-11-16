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
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakAll;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;

/**
 * Reports on a set of rules that indicate an issue with a breaks all dependency.
 */
public class BreaksAllError extends SolverError {
	final Set<ModLoadOption> from = new LinkedHashSet<>();
	final QuiltRuleBreakAll breakage;

	public BreaksAllError(ModLoadOption from, QuiltRuleBreakAll breakage) {
		this.from.add(from);
		this.breakage = breakage;
	}

	@Override
	public boolean mergeInto(SolverError into) {
		// BreaksAll are rare and there probably wont be duplicates.
		// Plus collection equals are difficult
		// We will just make sure we dont have multiple errors for the same rule

		if (into instanceof BreaksAllError) {
			BreaksAllError depDst = (BreaksAllError) into;
			return breakage == depDst.breakage;
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
		// "BuildCraft" breaks because all of Quilt Standard Libraries and Minecraft are present!
		// "BuildCraft" breaks because all of Quilt Standard Libraries, Minecraft, and Third Mod are present!

		// Description:
		// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
		ModLoadOption mandatoryMod = from.iterator().next();
		String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

		Iterator<ModDependency.Only> breaks = breakage.publicDep.iterator();
		QuiltLoaderText allMods = QuiltLoaderText.of(breaks.next().id().id());
		while (breaks.hasNext()) {
			ModDependency.Only next = breaks.next();

			if (breaks.hasNext()) {
				allMods = QuiltLoaderText.translate("error.break.join.title", allMods, next.id().id());
			} else {
				allMods = QuiltLoaderText.translate("error.break.join.last.title", allMods, next.id().id());
			}
		}

		QuiltLoaderText title = QuiltLoaderText.translate("error.breaks_all.title", rootModName, allMods);
		QuiltDisplayedError error = context.createError(title);

		setIconFromMod(error, mandatoryMod);

		for (QuiltRuleBreakOnly breakOnly : breakage.options) {
			error.appendDescription(VersionRangeDescriber.describe(breakOnly.publicDep.versionRange(), breakOnly.publicDep.id().id()));
			if (!breakOnly.publicDep.reason().isEmpty()) {
				error.appendDescription(QuiltLoaderText.translate("error.reason.specific", breakOnly.publicDep.id().id(), breakOnly.publicDep.reason()));
			}

			if (breakOnly.publicDep.unless() != null) {
				// [VERSION of MOD] can override this break if present.
				// [VERSION of MOD] overrides this break, but is unable to load due to another error.
				addUnless(error, breakOnly.publicDep, breakOnly.unless);
			}

			addFiles(error, context, "error.break.broken", breakOnly.getConflictingOptions());
			error.appendDescription(QuiltLoaderText.of(""));
		}


		addFiles(error, context, "error.break.breaking", from);

		addIssueLink(error, mandatoryMod);

		StringBuilder report = new StringBuilder(rootModName);
		report.append(" break");
		if (from.size() == 1) {
			report.append("s");
		}
		report.append(" because all of the following are present:");
		error.appendReportText(report.toString());
		for (QuiltRuleBreakOnly breakOnly : breakage.options) {
			report = new StringBuilder("- ");
			addVersionString(report, breakOnly.publicDep, false, true);
			report.append(breakOnly.publicDep.id()); // TODO
			report.append(":");
			error.appendReportText(report.toString());
			if (breakOnly.publicDep.unless() != null) {
				addUnlessClause(breakOnly.publicDep, rootModName, breakOnly.unless, from.size() == 1).forEach(line -> error.appendReportText("  " + line));
			}
			if (!breakOnly.publicDep.reason().isEmpty()) {
				error.appendReportText("  Breaking reason: " + breakOnly.publicDep.reason());
			}

			error.appendReportText("  Matching mods: ");
			for (ModLoadOption mod : breakOnly.getConflictingOptions()) {
				error.appendReportText("  " + getModReportLine(mod, context, true, !breakOnly.publicDep.versionRange().equals(VersionRange.ANY), false));
			}

			if (breakOnly.unless != null) {
				if (breakOnly.unless instanceof QuiltRuleDepOnly && !breakOnly.unless.getNodesTo().isEmpty()) {
					error.appendReportText("  Overriding mods: ");
					for (LoadOption option : breakOnly.unless.getNodesTo()) {
						if (option instanceof ModLoadOption) {
							error.appendReportText("  " + getModReportLine(((ModLoadOption) option), context, true, !((QuiltRuleDepOnly) breakOnly.unless).publicDep.versionRange().equals(VersionRange.ANY), false));
						} else {
							error.appendReportText("  - " + option.describe().toString());
						}
					}
				} else if (breakOnly.unless instanceof QuiltRuleDepAny) {
					boolean added = false;
					for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) breakOnly.unless).options) {
						for (LoadOption option : only.getNodesTo()) {
							if (!added) {
								error.appendReportText("  Overriding mods: ");
								added = true;
							}
							if (option instanceof ModLoadOption) {
								error.appendReportText("  " + getModReportLine(((ModLoadOption) option), context, true, !only.publicDep.versionRange().equals(VersionRange.ANY), false));
							} else {
								error.appendReportText("  - " + option.describe().toString());
							}
						}
					}
				}
			}
			error.appendReportText("");
		}

		error.appendReportText("Breaking mods: ");
		for (ModLoadOption mod : from) {
			getModReportLine(mod, context, true, false, false).forEach(error::appendReportText);
		}
	}
}
