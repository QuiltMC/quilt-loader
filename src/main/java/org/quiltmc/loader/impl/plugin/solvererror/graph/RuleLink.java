package org.quiltmc.loader.impl.plugin.solvererror.graph;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.Rule;

/**
 * A link that needs rule associated with it.
 *
 * @param <RULE> the type of the rule
 */
public interface RuleLink<RULE extends Rule> extends Link {
	/**
	 * @return the rule for this link
	 */
	@NotNull RULE getRule();
}
