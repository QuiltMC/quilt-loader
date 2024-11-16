package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;

/**
 * Represents a depends any that has some or all of its options.
 */
public class DependsAny implements DepLink<QuiltRuleDepAny> {
	@NotNull
	final Collection<LoadOption> depends;

	final QuiltRuleDepAny rule;

	public DependsAny(@NotNull Collection<LoadOption> depends, QuiltRuleDepAny rule) {
		this.depends = depends;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return depends.stream();
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), depends.hashCode() + 31);
		dotGraph.printf("\t%s [label=\"Any Of\", shape=\"invtriangle\"];\n", depends.hashCode() + 31);
		for (LoadOption b : depends) {
			dotGraph.printf("\t%s->%s [label=\"Depends\"];\n", depends.hashCode() + 31, b.hashCode());
		}
	}

	@Override
	public @NotNull QuiltRuleDepAny getRule() {
		return this.rule;
	}

	@Override
	public String toString() {
		return "DependsAny=" + depends;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DependsAny that = (DependsAny) o;

		return depends.equals(that.depends);
	}

	@Override
	public int hashCode() {
		return depends.hashCode();
	}
}
