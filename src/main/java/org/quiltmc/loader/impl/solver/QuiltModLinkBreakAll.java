package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;

public class QuiltModLinkBreakAll extends QuiltModLinkBreak {

	final QuiltModLinkBreakOnly[] options;
	final ModDependency.All publicDep;

	public QuiltModLinkBreakAll(Logger logger, RuleContext ctx, LoadOption option, ModDependency.All all) {

		super(option);
		this.publicDep = all;
		List<QuiltModLinkBreakOnly> optionList = new ArrayList<>();

		for (ModDependency.Only only : all) {
			if (!only.shouldIgnore()) {
				QuiltModDepOption sub = new QuiltModDepOption(only);
				ctx.addOption(sub);
				QuiltModLinkBreakOnly dep = new QuiltModLinkBreakOnly(logger, ctx, sub, only);
				ctx.addRule(dep);
				optionList.add(dep);
			}
		}

		this.options = optionList.toArray(new QuiltModLinkBreakOnly[0]);
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

		array[i] = source;
		definer.atMost(array.length - 1, array);
	}

	@Override
	boolean hasAnyConflictingOptions() {
		for (QuiltModLinkBreakOnly on : options) {
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
		for (QuiltModLinkBreakOnly on : options) {
			list.add(on.source);
		}
		return list;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}
}
