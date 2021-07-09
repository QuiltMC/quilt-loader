package org.quiltmc.loader.impl.solver;

import java.util.Collection;

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;

/** Base definition of a link between one or more {@link LoadOption}s, that */
// TODO: Rename this (and subclasses) to "Rule"
abstract class ModLink {

	public ModLink() {}

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the added option had no
	 *         affect on this rule. */
	abstract boolean onLoadOptionAdded(LoadOption option);

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the removed option had no
	 *         affect on this rule. */
	abstract boolean onLoadOptionRemoved(LoadOption option);

	abstract void define(RuleDefiner definer);

	@Deprecated
	public final void remove(DependencyHelper<LoadOption, ModLink> helper) {
		helper.quilt_removeConstraint(this);
	}

	/** @return A description of the link. */
	@Override
	public abstract String toString();

	/** Checks to see if this link is [...]
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public boolean isNode() {
		return true;
	}

	/** TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesFrom();

	/** TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesTo();

	public abstract void fallbackErrorDescription(StringBuilder errors);
}
