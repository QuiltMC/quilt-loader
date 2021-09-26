package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;

public class QuiltRuleBreakAll extends QuiltRuleBreak {

	final QuiltRuleBreakOnly[] options;
	final ModDependency.All publicDep;

	public QuiltRuleBreakAll(Logger logger, RuleContext ctx, LoadOption option, ModDependency.All all) {

		super(option);
		this.publicDep = all;
		List<QuiltRuleBreakOnly> optionList = new ArrayList<>();

		for (ModDependency.Only only : all) {
			if (!only.shouldIgnore()) {
				QuiltModDepOption sub = new QuiltModDepOption(only);
				ctx.addOption(sub);
				QuiltRuleBreakOnly dep = new QuiltRuleBreakOnly(logger, ctx, sub, only);
				ctx.addRule(dep);
				optionList.add(dep);
			}
		}

		this.options = optionList.toArray(new QuiltRuleBreakOnly[0]);
	}

	@Override
	boolean onLoadOptionAdded(LoadOption option) {
		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		return false;
	}

	@Override
	void define(RuleDefiner definer) {
		LoadOption[] array = new LoadOption[options.length + 1];
		int i = 0;

		for (; i < options.length; i++) {
			array[i] = definer.negate(options[i].source);
		}

		array[i] = source;
		definer.atMost(array.length - 1, array);
	}

	@Override
	boolean hasAnyConflictingOptions() {
		for (QuiltRuleBreakOnly on : options) {
			if (on.hasAnyConflictingOptions()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		// FIXME: This needs a proper toString()
		return "" + publicDep;
	}

	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.singleton(source);
	}

	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		List<LoadOption> list = new ArrayList<>();
		for (QuiltRuleBreakOnly on : options) {
			list.add(on.source);
		}
		return list;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Breakage for ");
		errors.append(source);
		errors.append(" on all of: ");

		for (QuiltRuleBreakOnly on : options) {
			errors.append("\n\t-");
			errors.append(on.source);
			errors.append(" ");
		}
	}
}
