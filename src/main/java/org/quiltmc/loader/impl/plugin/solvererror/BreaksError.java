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
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;

/**
 * Reports on a set of rules that indicate an issue with a breaking dependency.
 */
public class BreaksError extends SolverError {
	final Set<ModLoadOption> from = new LinkedHashSet<>();
	final QuiltRuleBreakOnly breakage;

	public BreaksError(ModLoadOption from, QuiltRuleBreakOnly breakage) {
		this.from.add(from);
		this.breakage = breakage;
	}

	@Override
	public boolean mergeInto(SolverError into) {
		if (into instanceof BreaksError) {
			BreaksError depDst = (BreaksError) into;
			if (!breakage.publicDep.id().equals(depDst.breakage.publicDep.id()) || !breakage.publicDep.versionRange().equals(depDst.breakage.publicDep.versionRange())) {
				return false;
			}
			depDst.from.addAll(from);
			return true;
		}
		return false;
	}

	@Override
	public boolean isRelatedOption(LoadOption option) {
		return from.contains(option) || this.breakage.getConflictingOptions().contains(option);
	}

	@Override
	public void report(SolverErrorReportContext context) {
		// Title:
		// "BuildCraft" breaks with [version 1.5.1] of "Quilt Standard Libraries", but it's present!

		// Description:
		// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
		ModLoadOption mandatoryMod = from.iterator().next();
		String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

		QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, breakage.publicDep.versionRange(), breakage.publicDep.id().id(), false, false);

		Object[] secondData = new Object[breakage.getConflictingOptions().size() == 1 ? 1 : 0];
		String secondKey = "error.break.";
		if (breakage.getConflictingOptions().size() > 1) {
			secondKey += "multi_conflict";
		} else {
			secondKey += "single_conflict";
			secondData[0] = breakage.getConflictingOptions().iterator().next().version().toString();
		}
		QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
		QuiltLoaderText title = QuiltLoaderText.translate("error.break.join.title", first, second);

		if (breakage.publicDep.versionRange().equals(VersionRange.ANY)) {
			title = QuiltLoaderText.translate(secondKey + ".all.title", rootModName, breakage.publicDep.id().id());
		}

		QuiltDisplayedError error = context.createError(title);
		setIconFromMod(error, mandatoryMod);

		if (!breakage.publicDep.reason().isEmpty()) {
			error.appendDescription(QuiltLoaderText.translate("error.reason", breakage.publicDep.reason()));
		}

		if (breakage.publicDep.unless() != null) {
			addUnless(error, breakage.publicDep, breakage.unless);
			// A newline after the reason was desired here, but do you think Swing loves nice things?
		}

		addFiles(error, context, "error.break.breaking", from);
		addFiles(error, context, "error.break.broken", breakage.getConflictingOptions());
		addIssueLink(error, mandatoryMod);

		StringBuilder report = new StringBuilder(rootModName);
		report.append(" break");
		if (from.size() == 1) {
			report.append("s");
		}
		addVersionString(report, breakage.publicDep, true, false);
		report.append(breakage.publicDep.id());// TODO
		report.append(", which is present!");
		error.appendReportText(report.toString());
		if (breakage.publicDep.unless() != null) {
			addUnlessClause(breakage.publicDep, rootModName, breakage.unless, from.size() == 1).forEach(error::appendReportText);
		}
		if (!breakage.publicDep.reason().isEmpty()) {
			error.appendReportText("Breaking mod's reason: " + breakage.publicDep.reason(), "");
		}
		error.appendReportText("");

		error.appendReportText("Breaking mods: ");
		for (ModLoadOption mod : from) {
			getModReportLine(mod, context, true, false, false).forEach(error::appendReportText);
		}

		error.appendReportText("", "Broken mods: ");
		for (ModLoadOption mod : breakage.getConflictingOptions()) {
			getModReportLine(mod, context, true, !breakage.publicDep.versionRange().equals(VersionRange.ANY), false).forEach(error::appendReportText);
		}

		if (breakage.unless != null) {
			if (breakage.unless instanceof QuiltRuleDepOnly && !breakage.unless.getNodesTo().isEmpty()) {
				error.appendReportText("", "Overriding mods: ");
				for (LoadOption option : breakage.unless.getNodesTo()) {
					if (option instanceof ModLoadOption) {
						getModReportLine(((ModLoadOption) option), context, true, true, false).forEach(error::appendReportText);
					} else {
						error.appendReportText("- " + option.describe().toString());
					}
				}
			} else if (breakage.unless instanceof QuiltRuleDepAny) {
				boolean added = false;
				for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) breakage.unless).options) {
					for (LoadOption option : only.getNodesTo()) {
						if (!added) {
							error.appendReportText("", "Overriding mods: ");
							added = true;
						}
						if (option instanceof ModLoadOption) {
							getModReportLine(((ModLoadOption) option), context, true, true, false).forEach(error::appendReportText);
						} else {
							error.appendReportText("- " + option.describe().toString());
						}
					}
				}
			}
		}
	}
}
