package org.quiltmc.loader.impl.solver;

abstract class QuiltModLinkBreak extends ModLink {
	final LoadOption source;

	public QuiltModLinkBreak(LoadOption source) {
		this.source = source;
	}

	abstract boolean hasAnyConflictingOptions();
}
