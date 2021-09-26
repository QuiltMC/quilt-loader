package org.quiltmc.loader.impl.solver;

abstract class QuiltRuleBreak extends Rule {
	final LoadOption source;

	public QuiltRuleBreak(LoadOption source) {
		this.source = source;
	}

	abstract boolean hasAnyConflictingOptions();
}
