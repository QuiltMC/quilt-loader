package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.plugin.solver.LoadOption;

/**
 * Represents a link between to options where one provides the other.
 */
public class Provided implements Link {
	@NotNull
	final LoadOption provides;

	public Provided(@NotNull LoadOption provides) {
		this.provides = provides;
	}

	public Stream<LoadOption> children() {
		return Stream.of(provides);
	}

	@Override
	public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		dotGraph.printf("\t%s->%s [label=\"Provides\", style=\"dashed\"];\n", from.hashCode(), provides.hashCode());
	}

	@Override
	public String toString() {
		return "Provided=" + provides;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Provided provided = (Provided) o;

		return provides.equals(provided.provides);
	}

	@Override
	public int hashCode() {
		return provides.hashCode();
	}
}
