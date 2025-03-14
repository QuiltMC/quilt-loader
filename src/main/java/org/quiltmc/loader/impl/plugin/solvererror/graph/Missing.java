package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;

/**
 * Represents a depend that is missing its requirement.
 */
public class Missing implements DepLink<QuiltRuleDepOnly> {
	@NotNull
	final QuiltRuleDepOnly rule;
	@NotNull
	final ModDependency.Only missing;

	public Missing(@NotNull ModDependency.Only missing, @NotNull QuiltRuleDepOnly rule) {
		this.missing = missing;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return Stream.empty();
	}

	@Override
	public @NotNull QuiltRuleDepOnly getRule() {
		return this.rule;
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
		dotGraph.printf("\t%s [label=\"Missing: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.id());
	}

	@Override
	public String toString() {
		return "Missing=" + missing.id();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Missing missing1 = (Missing) o;

		if (!rule.equals(missing1.rule)) return false;
		return missing.equals(missing1.missing);
	}

	@Override
	public int hashCode() {
		int result = rule.hashCode();
		result = 31 * result + missing.hashCode();
		return result;
	}
}
