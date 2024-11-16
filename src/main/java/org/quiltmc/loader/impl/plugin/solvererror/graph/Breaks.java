package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;

/**
 * Represents a breaks that has its requirement.
 */
public class Breaks implements RuleLink<QuiltRuleBreakOnly> {
	@NotNull
	final QuiltRuleBreakOnly rule;
	@NotNull
	final LoadOption breaks;

	public Breaks(@NotNull LoadOption breaks, @NotNull QuiltRuleBreakOnly rule) {
		this.breaks = breaks;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return Stream.of(breaks);
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
		if (rule.unless != null) {
			if (rule.unless instanceof QuiltRuleDepOnly) {
				for (LoadOption option : rule.unless.getNodesTo()) {
					dotGraph.printf("\t%s->%s [label=\"Unless\", color=blue];\n", from.hashCode(), option.hashCode());
				}
			} else if (rule.unless instanceof QuiltRuleDepAny) {
				for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) rule.unless).options) {
					for (LoadOption option : only.getNodesTo()) {
						dotGraph.printf("\t%s->%s [label=\"Unless\", color=blue];\n", from.hashCode(), option.hashCode());
					}
				}
			}
		}
	}

	@Override
	public @NotNull QuiltRuleBreakOnly getRule() {
		return rule;
	}

	@Override
	public String toString() {
		return "Breaks=" + breaks;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Breaks breaks1 = (Breaks) o;

		if (!rule.equals(breaks1.rule)) return false;
		return breaks.equals(breaks1.breaks);
	}

	@Override
	public int hashCode() {
		int result = rule.hashCode();
		result = 31 * result + breaks.hashCode();
		return result;
	}
}
