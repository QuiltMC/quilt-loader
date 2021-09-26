package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;

public class QuiltRuleDepAny extends QuiltRuleDep {

	final QuiltRuleDepOnly[] options;
	final ModDependency.Any publicDep;

	public QuiltRuleDepAny(Logger logger, RuleContext ctx, LoadOption option, ModDependency.Any any) {

		super(option);
		this.publicDep = any;
		List<QuiltRuleDepOnly> optionList = new ArrayList<>();

		for (ModDependency.Only only : any) {
			if (!only.shouldIgnore()) {
				QuiltModDepOption sub = new QuiltModDepOption(only);
				ctx.addOption(sub);
				QuiltRuleDepOnly dep = new QuiltRuleDepOnly(logger, ctx, sub, only);
				ctx.addRule(dep);
				optionList.add(dep);
			}
		}

		this.options = optionList.toArray(new QuiltRuleDepOnly[0]);
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
			array[i] = options[i].source;
		}
		array[i] = definer.negate(source);
		definer.atLeastOneOf(array);
	}

	@Override
	boolean hasAnyValidOptions() {
		for (QuiltRuleDepOnly on : options) {
			if (on.hasAnyValidOptions()) {
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
		for (QuiltRuleDepOnly on : options) {
			list.add(on.source);
		}
		return list;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Dependancy for ");
		errors.append(source);
		errors.append(" on any of: ");

		for (QuiltRuleDepOnly on : options) {
			errors.append("\n\t-");
			errors.append(on.source);
			errors.append(" ");
		}
	}
}
