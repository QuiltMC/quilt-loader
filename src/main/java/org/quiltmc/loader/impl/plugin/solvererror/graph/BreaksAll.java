package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakAll;

/**
 * Represents a breaks all that is has all of its requirements.
 */
public class BreaksAll implements RuleLink<QuiltRuleBreakAll> {
	@NotNull
	final Collection<LoadOption> breaks;
	@NotNull
	final QuiltRuleBreakAll rule;

	public BreaksAll(@NotNull Collection<LoadOption> breaks, @NotNull QuiltRuleBreakAll rule) {
		this.breaks = breaks;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return breaks.stream();
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
		dotGraph.printf("\t%s [label=\"All Of\", shape=\"invtriangle\", color=red];\n", breaks.hashCode());
		for (LoadOption b : breaks) {
			dotGraph.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", breaks.hashCode(), b.hashCode());
		}
	}

	@Override
	public @NotNull QuiltRuleBreakAll getRule() {
		return this.rule;
	}

	@Override
	public String toString() {
		return "BreaksAll=" + breaks;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BreaksAll breaksAll = (BreaksAll) o;

		if (!breaks.equals(breaksAll.breaks)) return false;
		return rule.equals(breaksAll.rule);
	}

	@Override
	public int hashCode() {
		int result = breaks.hashCode();
		result = 31 * result + rule.hashCode();
		return result;
	}

	public Collection<LoadOption> breaks() {
		return this.breaks;
	}
}
