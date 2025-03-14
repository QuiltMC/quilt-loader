package org.quiltmc.loader.impl.plugin.solvererror;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.plugin.SolverErrorReportContext;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;

/**
 * Reports a set of rules that cause an unknown error.
 */
public class UnhandledError extends SolverError {

	final Collection<Rule> rules;

	public UnhandledError(Collection<Rule> rules) {
		this.rules = new LinkedHashSet<>(rules);
	}

	@Override
	public boolean isRelatedOption(LoadOption option) {
		return false;
	}

	@Override
	public boolean mergeInto(SolverError into) {
		if (into instanceof UnhandledError) {
			UnhandledError depDst = (UnhandledError) into;
			if (depDst.rules.containsAll(rules)) { // If the other error has all the same rules as us, we are just a subset
				return true;
			} else if (rules.containsAll(depDst.rules)) { // We have the same but more rules than the current error, add ours to it
				depDst.rules.addAll(rules);
				return true;
			}
		}
		return false;
	}

	@Override
	public void report(SolverErrorReportContext context) {
		QuiltDisplayedError error = context.createError(QuiltLoaderText.translate("error.unhandled_solver"));

		error.appendDescription(QuiltLoaderText.translate("error.unhandled_solver.desc"));
		error.addOpenQuiltSupportButton();
		error.appendReportText("Unhandled solver error involving the following rules:");

		StringBuilder sb = new StringBuilder();
		int number = 1;
		for (Rule rule : rules) {
			error.appendDescription(QuiltLoaderText.translate("error.unhandled_solver.desc.rule_n", number, rule.getClass()));
			rule.appendRuleDescription(error::appendDescription);
			error.appendReportText("Rule " + number++ + ":");
			sb.setLength(0);
			// TODO: Rename 'fallbackErrorDescription'
			// to something like 'fallbackReportDescription'
			// and then clean up all of the implementations to be more readable.
			rule.fallbackErrorDescription(sb);
			error.appendReportText(sb.toString());
		}
		error.appendReportText("");
	}
}
