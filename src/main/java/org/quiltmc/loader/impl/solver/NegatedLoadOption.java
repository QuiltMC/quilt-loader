package org.quiltmc.loader.impl.solver;

/** Used for the "inverse load" condition - if this is required by a {@link ModLink} then it means the
 * {@link LoadOption} must not be loaded. */
final class NegatedLoadOption extends LoadOption {
	final LoadOption not;

	public NegatedLoadOption(LoadOption not) {
		this.not = not;
	}

	@Override
	public String toString() {
		return "NOT " + not;
	}
}
