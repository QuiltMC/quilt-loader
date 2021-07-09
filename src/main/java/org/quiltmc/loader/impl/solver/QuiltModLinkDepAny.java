package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

public class QuiltModLinkDepAny extends QuiltModLinkDep {

	final QuiltModLinkDepOnly[] options;
	final ModDependency.Any publicDep;

	public QuiltModLinkDepAny(Logger logger, RuleContext ctx, LoadOption option, ModDependency.Any any) {

		super(option);
		this.publicDep = any;
		List<QuiltModLinkDepOnly> optionList = new ArrayList<>();

		for (ModDependency.Only only : any) {
			if (!only.shouldIgnore()) {
				QuiltModDepOption sub = new QuiltModDepOption(only);
				ctx.addOption(sub);
				QuiltModLinkDepOnly dep = new QuiltModLinkDepOnly(logger, ctx, sub, only);
				ctx.addRule(dep);
				optionList.add(dep);
			}
		}

		this.options = optionList.toArray(new QuiltModLinkDepOnly[0]);
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
		for (QuiltModLinkDepOnly on : options) {
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
