package org.quiltmc.loader.impl.plugin.solvererror.graph;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.plugin.solvererror.SolverError;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/**
 * Represents a graph of all the rules reported in the errors.
 */
public class Graph {
	final Set<LoadOption> nodes = new LinkedHashSet<>();
	final Map<LoadOption, Set<Link>> edges = new LinkedHashMap<>();
	final Map<LoadOption, Set<Link>> reverseEdges = new LinkedHashMap<>();
	final Map<Link, LoadOption> parents = new LinkedHashMap<>();

	/**
	 * @param option the load option
	 * @return the dot graph node label for the option
	 */
	private static String label(LoadOption option) {
		if (option instanceof ModLoadOption) {
			return ((ModLoadOption) option).id() + "\\n" + ((ModLoadOption) option).version();
		}

		return option.toString();
	}


	/**
	 * Figures out if a specific load option is mandatory.
	 * This also returns true for mods provided by mandatory mods.
	 *
	 * @param option the load option
	 * @return {@code true} if the option is a mandatory mod, {@code false} otherwise
	 */
	public boolean isMandatory(LoadOption option) {
		boolean mandatory = edgesTo(option).stream().anyMatch(Mandatory.class::isInstance);

		mandatory |= edgesTo(option)
				.stream()
				.filter(Provided.class::isInstance)
				.map(parents::get)
				.filter(Objects::nonNull)
				.anyMatch(this::isMandatory);

		return mandatory;
	}

	/**
	 * @param option the load option
	 * @return {@code true} if the load option is provided by another option, {@code false} otherwise
	 */
	public boolean isProvided(LoadOption option) {
		return this.edgesTo(option).stream().anyMatch(Provided.class::isInstance);
	}

	public ModLoadOption getProvidingMod(LoadOption option) {
		return (ModLoadOption) parents.get(edgesTo(option).stream().filter(Provided.class::isInstance).findFirst().get());
	}

	/**
	 * Figures out if a specific load option is required.
	 * If the option is provided, it will also check if the providing option is required.
	 *
	 * @param option the load option
	 * @return {@code true} if the load option is depended on by another option, {@code false} otherwise
	 */
	public boolean isDepended(LoadOption option) {
		boolean depended = edgesTo(option).stream().anyMatch(Depends.class::isInstance);

		if (this.isProvided(option)) {
			depended |= this.edgesTo(option).stream()
					.filter(Provided.class::isInstance)
					.map(parents::get)
					.filter(Objects::nonNull)
					.anyMatch(this::isDepended);
		}

		return depended;
	}

	/**
	 * Logs the graph to the info logger.
	 */
	public void logGraph() {
		StringWriter dotGraphString = new StringWriter();
		PrintWriter dotGraph = new PrintWriter(dotGraphString);
		dotGraph.println("digraph G {");
		dotGraph.println("\troot[style=invis];");
		dotGraph.println("\tsubgraph cluster_root {");
		nodes
				.stream()
				.filter(this::isMandatory)
				.forEach(option -> dotGraph.printf("\t\t%s[label=\"%s\", shape=\"Mdiamond\"];\n", option.hashCode(), label(option)));
		dotGraph.print("\t\tstyle=invis;\n");
		dotGraph.print("\t}\n");
		nodes
				.forEach(option -> {
					if (this.isMandatory(option)) {
						dotGraph.printf("\troot->%s[style=invis];\n", option.hashCode());
					} else {
						dotGraph.printf("\t%s[label=\"%s\", shape=\"rectangle\"];\n", option.hashCode(), label(option));
					}
				});

		nodes.forEach(node -> edges(node).forEach(link -> link.dotGraphEdge(node, dotGraph)));
		nodes.stream()
				.flatMap(node -> edgesTo(node).stream().filter(RootLink.class::isInstance))
				.distinct()
				.forEach(link -> link.dotGraphEdge(null, dotGraph));
		dotGraph.println("}");
		dotGraph.println();

		Log.info(LogCategory.SOLVING, dotGraphString.toString());
	}

	/**
	 * Adds a link to the graph.
	 *
	 * @param from the parent option
	 * @param link the link
	 */
	public void addLink(@Nullable LoadOption from, Link link) {
		if (from != null) {
			edges.computeIfAbsent(from, option -> new LinkedHashSet<>()).add(link);
			nodes.add(from);

			LoadOption oldParent = parents.put(link, from);
			if (oldParent != null && !Objects.equals(from, oldParent)) {
				throw new IllegalStateException("this shouldn't happen");
			}
		}

		link.children().forEach(to -> {
			reverseEdges.computeIfAbsent(to, option -> new HashSet<>()).add(link);
			nodes.add(to);
		});
	}

	/**
	 * @param from the parent option
	 * @return the set of links from the option
	 */
	public Set<Link> edges(LoadOption from) {
		return edges.getOrDefault(from, new LinkedHashSet<>());
	}

	/**
	 * @param to the child option
	 * @return the set of links to the option
	 */
	public Set<Link> edgesTo(LoadOption to) {
		return reverseEdges.getOrDefault(to, new LinkedHashSet<>());
	}

	/**
	 * Prunes rules that provide no additional information to the graph, such as options from technical load options.
	 */
	public void clean() {
		boolean modified = true;
		while (modified) {
			modified = false;

			Set<LoadOption> noLinks = this.parents.values()
					.stream()
					.filter(option -> this.edgesTo(option).isEmpty())
					.collect(Collectors.toCollection(HashSet::new));

			for (LoadOption o : noLinks) {
				nodes.remove(o);
				Set<Link> removedLinks = this.edges.remove(o);
				for (Link link : removedLinks) {
					this.parents.remove(link);
				}
				modified = true;
			}
		}
	}

	public Set<LoadOption> nodes() {
		return nodes;
	}

	public boolean isBreaking(LoadOption option) {
		return this.edges(option).stream().anyMatch(((Predicate<Link>) Breaks.class::isInstance).or(BreaksAll.class::isInstance));
	}

	public boolean isBroken(LoadOption option) {
		return this.edgesTo(option).stream().anyMatch(((Predicate<Link>) Breaks.class::isInstance).or(BreaksAll.class::isInstance));
	}

	public boolean isProviding(LoadOption option) {
		return this.edges(option).stream().anyMatch(Provided.class::isInstance);
	}

	public boolean isDuplicating(LoadOption option) {
		return this.edgesTo(option).stream().anyMatch(Duplicates.class::isInstance);
	}
}
