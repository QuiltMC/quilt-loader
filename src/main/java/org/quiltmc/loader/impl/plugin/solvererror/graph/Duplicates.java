package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import org.quiltmc.loader.api.plugin.solver.LoadOption;

/**
 * Represents a set of mods that all provide the same id.
 */
public class Duplicates implements RootLink {
	@NotNull
	final Collection<LoadOption> options;
	@NotNull
	final String id;

	public Duplicates(@NotNull Collection<LoadOption> options, @NotNull String id) {
		this.options = options;
		this.id = id;
	}

	public Stream<LoadOption> children() {
		return options.stream();
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s[label=\"%s\"];\n", id, id);
		for (LoadOption option : options) {
			dotGraph.printf("\t%s->%s [label=\"Duplicates\", style=\"dashed\"];\n", option.hashCode(), id);
		}
	}

	@Override
	public String toString() {
		return "Duplicates=" + options;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Duplicates that = (Duplicates) o;

		if (!options.equals(that.options)) return false;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		int result = options.hashCode();
		result = 31 * result + id.hashCode();
		return result;
	}

	public @NotNull Collection<LoadOption> options() {
		return options;
	}

	public @NotNull String id() {
		return id;
	}
}
