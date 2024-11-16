package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.util.stream.Stream;

import org.quiltmc.loader.api.plugin.solver.LoadOption;

/**
 * A link between mod options that indicates some form of relationship.
 */
public interface Link {
	/**
	 * @return the children for this load option
	 */
	Stream<LoadOption> children();

	/**
	 * Adds the edge(s) for this link to the dot graph.
	 *
	 * @param from     the parent for this link
	 * @param dotGraph the graph output
	 */
	default void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		this.children().forEach(child -> {
			dotGraph.printf("\t%s->%s [label=\"%s\"];\n", from.hashCode(), child.hashCode(), this.getClass().getSimpleName());
		});
	}
}
