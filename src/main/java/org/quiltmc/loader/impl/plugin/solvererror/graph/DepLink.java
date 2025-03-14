package org.quiltmc.loader.impl.plugin.solvererror.graph;

import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDep;

/**
 * A depend or depends any rule wrapper for code quality.
 *
 * @param <RULE> the rule type
 */
public interface DepLink<RULE extends QuiltRuleDep> extends RuleLink<RULE> {
}
