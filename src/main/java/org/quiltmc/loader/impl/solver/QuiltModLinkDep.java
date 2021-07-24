package org.quiltmc.loader.impl.solver;

abstract class QuiltModLinkDep extends ModLink {
	final LoadOption source;

	public QuiltModLinkDep(LoadOption source) {
		this.source = source;
	}

	abstract boolean hasAnyValidOptions();
}
