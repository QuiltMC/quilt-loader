package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;

/**
 * Represents a depends any that is missing all of its options.
 */
public class MissingAny implements DepLink<QuiltRuleDepAny> {
	@NotNull
	final ModDependency.Any missing;

	final QuiltRuleDepAny rule;

	public MissingAny(@NotNull ModDependency.Any missing, QuiltRuleDepAny rule) {
		this.missing = missing;
		this.rule = rule;
	}

	public Stream<LoadOption> children() {
		return Stream.empty();
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
		dotGraph.printf("\t%s [label=\"Missing any of: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.stream().map(ModDependency.Only::id).map(ModDependencyIdentifier::toString).collect(Collectors.joining(", ")));
	}

	@Override
	public @NotNull QuiltRuleDepAny getRule() {
		return this.rule;
	}

	@Override
	public String toString() {
		return "MissingAny=[" + missing.stream().map(ModDependency.Only::id).map(ModDependencyIdentifier::toString).collect(Collectors.joining(", ")) + "]";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MissingAny that = (MissingAny) o;

		return missing.equals(that.missing);
	}

	@Override
	public int hashCode() {
		return missing.hashCode();
	}
}
