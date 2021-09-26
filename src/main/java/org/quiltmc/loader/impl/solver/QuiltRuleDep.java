package org.quiltmc.loader.impl.solver;

abstract class QuiltRuleDep extends Rule {
	final LoadOption source;

	public QuiltRuleDep(LoadOption source) {
		this.source = source;
	}

	abstract boolean hasAnyValidOptions();
}
