/*
 * Copyright 2022, 2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.ModDependency.Only;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.plugin.quilt.MandatoryModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.OptionalModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.ProvidedModOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakAll;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDep;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class SolverErrorHelper {
	private final QuiltPluginManagerImpl manager;
	private final List<SolverError> errors = new ArrayList<>();

	private final Graph graph = new Graph();

	SolverErrorHelper(QuiltPluginManagerImpl manager) {
		this.manager = manager;
	}

	/**
	 * Reports all the errors to the plugin manager.
	 * <p>
	 *
	 * If the system property {@value org.quiltmc.loader.impl.util.SystemProperties#PRINT_MOD_SOLVING_ERROR_DOT_GRAPH} is true,
	 * the dot graph for the errors is logged to the info logger.
	 */
	void reportErrors() {
		if (SystemProperties.getBoolean(SystemProperties.PRINT_MOD_SOLVING_ERROR_DOT_GRAPH, false)) {
			graph.logGraph();
		}
		for (SolverError error : errors) {
			error.report(manager);
		}
	}

	/**
	 * Adds a new error to the list of errors.
	 *
	 * @param error the new error.
	 */
	private void addError(SolverError error) {
		for (SolverError e2 : errors) {
			if (error.mergeInto(e2)) {
				return;
			}
		}
		errors.add(error);
	}

	/**
	 * Reports human understandable errors from the solver rules.
	 *
	 * @param rules the rules that cause a solver error.
	 */
	void reportSolverError(Collection<Rule> rules) {
		addRulesToGraph(rules);

		graph.clean();

		if (!reportGraphErrors()) {
			addError(new UnhandledError(graph, rules));
		}
	}

	/**
	 * Adds the rules to the graph. Since it is very likely that the same rule comes through multiple times,
	 * it is important that {@link Link}s override {@link Object#equals(Object)} and {@link Object#hashCode()}.
	 *
	 * @param rules the rules to convert to links in the graph.
	 */
	private void addRulesToGraph(Collection<Rule> rules) {
		for (Rule rule : rules) {
			if (rule instanceof QuiltRuleBreakAll) {
				Set<LoadOption> breaks = rule.getNodesTo()
						.stream()
						.flatMap(loadOption -> rules.stream().filter(r -> r.getNodesFrom().contains(loadOption)))
						.flatMap(r -> r.getNodesTo().stream())
						.collect(Collectors.toSet());

				for (LoadOption load : rule.getNodesFrom()) {
					graph.addLink(load, new BreaksAll(breaks, (QuiltRuleBreakAll) rule));
				}
			} else if (rule instanceof QuiltRuleBreakOnly) {
				LoadOption breaks = rule.getNodesTo().stream()
						.findFirst()
						.get();

				for (LoadOption load : rule.getNodesFrom()) {
					graph.addLink(load, new Breaks(breaks, ((QuiltRuleBreakOnly) rule)));
				}
			} else if (rule instanceof QuiltRuleDepAny) {
				Set<LoadOption> depends = rule.getNodesTo()
						.stream()
						.flatMap(loadOption -> rules.stream().filter(r -> r.getNodesFrom().contains(loadOption)))
						.flatMap(r -> r.getNodesTo().stream())
						.collect(Collectors.toSet());

				for (LoadOption load : rule.getNodesFrom()) {
					if (!depends.isEmpty()) {
						graph.addLink(load, new DependsAny(depends, (QuiltRuleDepAny) rule));
					} else {
						graph.addLink(load, new MissingAny(
								((QuiltRuleDepAny) rule).publicDep,
								(QuiltRuleDepAny) rule
						));
					}
				}
			} else if (rule instanceof QuiltRuleDepOnly) {
				Optional<LoadOption> depends = rule.getNodesTo()
						.stream()
						.map(LoadOption.class::cast)
						.findFirst();

				for (LoadOption load : rule.getNodesFrom()) {
					if (depends.isPresent()) {
						graph.addLink(load, new Depends(depends.get(), (QuiltRuleDepOnly) rule));
					} else {
						graph.addLink(load, new Missing(
								((QuiltRuleDepOnly) rule).publicDep,
								(QuiltRuleDepOnly) rule
						));
					}
				}
			} else if (rule instanceof MandatoryModIdDefinition) {
				for (LoadOption load : rule.getNodesTo()) {
					graph.addLink(null, new Mandatory(load));
				}
			} else if (rule instanceof OptionalModIdDefinition) {
				OptionalModIdDefinition definition = (OptionalModIdDefinition) rule;

				Collection<? extends LoadOption> nodesTo = rule.getNodesTo();
				if (nodesTo.size() > 1) {
					graph.addLink(
							null,
							new Duplicates(
									new ArrayList<>(nodesTo),
									definition.getModId()
							)
					);
				}

				for (LoadOption loadOption : nodesTo) {
					if (loadOption instanceof ProvidedModOption) {
						ProvidedModOption provided = (ProvidedModOption) loadOption;
						graph.addLink(provided.getTarget(), new Provided(provided));
					}
				}
			} else {
				Log.warn(LogCategory.SOLVING, "Unknown rule: %s -> ", rule.getClass().getSimpleName());
				rule.appendRuleDescription(text -> Log.warn(LogCategory.SOLVING, text.toString()));
			}
		}
	}

	/**
	 * Reports all the errors found in the graph. This method will be continuously called on the same graph as it builds its links.
	 * Because of this, its really important that {@link SolverError}s override {@link SolverError#mergeInto(SolverError)} correctly.
	 *
	 * @return {@code true} if an error was found, {@code false} if not and an unknown error should be reported.
	 */
	private boolean reportGraphErrors() {
		try {
			boolean added = false;
			for (LoadOption option : graph.nodes) {
				for (Link link : graph.edges(option)) {
					if (link instanceof Breaks) {
						Breaks breaks = ((Breaks) link);
						ModLoadOption from = (ModLoadOption) option;
						this.addError(new BreaksError(graph, from, breaks.getRule()));
						added = true;
					} else if (link instanceof BreaksAll) {
						BreaksAll breaksAll = ((BreaksAll) link);
						ModLoadOption from = (ModLoadOption) option;
						if (breaksAll.breaks.size() == 1) {
							this.addError(new BreaksError(graph, from, breaksAll.getRule().options[0]));
						} else {
							this.addError(new BreaksAllError(graph, from, breaksAll.getRule()));
						}
						added = true;
					} else if (link instanceof DepLink) {
						DepLink<?> dep = ((DepLink<?>) link);
						ModLoadOption from = (ModLoadOption) option;

						Set<QuiltRuleDepOnly> rules = flattenUnless(dep.getRule());
						if (rules.size() > 1) {
							this.addError(new DependsAnyError(graph, from, rules));
						} else {
							this.addError(new DependsError(graph, from, rules.iterator().next()));
						}

						added = true;
					}
				}

				for (Link link : graph.edgesTo(option)) {
					if (link instanceof Duplicates) {
						Duplicates duplicates = (Duplicates) link;
						this.addError(new DuplicatesError(graph, duplicates.id, duplicates.options));
						added = true;
					}
				}
			}
			return added;
		} catch (Exception e) {
			Log.error(LogCategory.SOLVING, "Unknown error detecting solver errors!", e);
			return false;
		}
	}

	/**
	 * Flattens a {@link QuiltRuleDep} into a set of {@link QuiltRuleDepOnly}s, including all the unless clauses.
	 * @param rule the rule to flatten
	 * @return a set of all the possible {@link QuiltRuleDepOnly}s
	 */
	private static Set<QuiltRuleDepOnly> flattenUnless(QuiltRuleDep rule) {
		Set<QuiltRuleDepOnly> rules = new HashSet<>();

		if (rule instanceof QuiltRuleDepOnly) {
			rules.add((QuiltRuleDepOnly) rule);
			if (((QuiltRuleDepOnly) rule).unless != null) {
				rules.addAll(flattenUnless(((QuiltRuleDepOnly) rule).unless));
			}
		} else if (rule instanceof QuiltRuleDepAny) {
			for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) rule).options) {
				rules.addAll(flattenUnless(only));
			}
		}

		return rules;
	}

	/**
	 * Represents a graph of all the rules reported in the errors.
	 */
	static class Graph {
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
		 * @param option the load option
		 * @return {@code true} if the option is a mandatory mod, {@code false} otherwise
		 */
		boolean isMandatory(LoadOption option) {
			boolean mandatory = edgesTo(option).stream().anyMatch(Mandatory.class::isInstance);

			mandatory |= edgesTo(option)
					.stream()
					.filter(Provided.class::isInstance)
					.map(parents::get)
					.filter(Objects::nonNull)
					.map(this::edgesTo)
					.flatMap(Collection::stream)
					.anyMatch(Mandatory.class::isInstance);

			return mandatory;
		}

		/**
		 * Logs the graph to the info logger.
		 */
		void logGraph() {
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
	}

	/**
	 * A link between mod options that indicates some form of relationship.
	 */
	interface Link {
		/**
		 * @return the children for this load option
		 */
		Stream<LoadOption> children();

		/**
		 * Adds the edge(s) for this link to the dot graph.
		 *
		 * @param from the parent for this link
		 * @param dotGraph the graph output
		 */
		default void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
			this.children().forEach(child -> {
				dotGraph.printf("\t%s->%s [label=\"%s\"];\n", from.hashCode(), child.hashCode(), this.getClass().getSimpleName());
			});
		}
	}

	/**
	 * Represents a link that does not represent a link between two options.
	 */
	interface RootLink extends Link {
	}

	/**
	 * A link that needs rule associated with it.
	 * @param <RULE> the type of the rule
	 */
	interface RuleLink<RULE extends Rule> extends Link {
		/**
		 * @return the rule for this link
		 */
		@NotNull RULE getRule();
	}

	/**
	 * Represents a breaks that has its requirement.
	 */
	static class Breaks implements RuleLink<QuiltRuleBreakOnly> {
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

	/**
	 * Represents a breaks all that is has all of its requirements.
	 */
	static class BreaksAll implements RuleLink<QuiltRuleBreakAll> {
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
	}

	/**
	 * A depend or depends any rule wrapper for code quality.
	 * @param <RULE> the rule type
	 */
	interface DepLink<RULE extends QuiltRuleDep> extends RuleLink<RULE> {
	}

	/**
	 * Represents a depend that has its requirement.
	 */
	static class Depends implements DepLink<QuiltRuleDepOnly> {
		@NotNull
		final QuiltRuleDepOnly rule;
		@NotNull
		final LoadOption depends;

		public Depends(@NotNull LoadOption depends, @NotNull QuiltRuleDepOnly rule) {
			this.depends = depends;
			this.rule = rule;
		}

		public Stream<LoadOption> children() {
			return Stream.of(depends);
		}

		@Override
		public @NotNull QuiltRuleDepOnly getRule() {
			return this.rule;
		}

		@Override
		public String toString() {
			return "Depends=" + depends;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Depends depends1 = (Depends) o;

			if (!rule.equals(depends1.rule)) return false;
			return depends.equals(depends1.depends);
		}

		@Override
		public int hashCode() {
			int result = rule.hashCode();
			result = 31 * result + depends.hashCode();
			return result;
		}
	}

	/**
	 * Represents a depends any that has some or all of its options.
	 */
	static class DependsAny implements DepLink<QuiltRuleDepAny> {
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

	/**
	 * Represents a depend that is missing its requirement.
	 */
	static class Missing implements DepLink<QuiltRuleDepOnly> {
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

	/**
	 * Represents a depends any that is missing all of its options.
	 */
	static class MissingAny implements DepLink<QuiltRuleDepAny> {
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

	/**
	 * Represents a set of mods that all provide the same id.
	 */
	static class Duplicates implements RootLink {
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
	}

	/**
	 * Represents a link that a mod is mandatory and must be loaded.
	 */
	static class Mandatory implements RootLink {
		@NotNull
		final LoadOption option;

		public Mandatory(@NotNull LoadOption option) {
			this.option = option;
		}

		public Stream<LoadOption> children() {
			return Stream.of(option);
		}

		@Override
		public void dotGraphEdge(LoadOption from, PrintWriter dotGraph) {
		}

		@Override
		public String toString() {
			return "Manditory=" + option;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Mandatory mandatory = (Mandatory) o;

			return option.equals(mandatory.option);
		}

		@Override
		public int hashCode() {
			return option.hashCode();
		}
	}

	/**
	 * Represents a link between to options where one provides the other.
	 */
	static class Provided implements Link {
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

	/**
	 * A solver error which might have multiple real errors merged into one for display.
	 */
	static abstract class SolverError {
		final Graph graph;

		public SolverError(Graph graph) {
			this.graph = graph;
		}

		/**
		 * Attempts to merge this error into the given error. This object itself shouldn't be modified.
		 *
		 * @return True if the destination object was modified (and this was merged into it), false otherwise.
		 */
		abstract boolean mergeInto(SolverError into);

		/**
		 * Adds the error to the plugin manager.
		 *
		 * @param manager the plugin manager
		 */
		abstract void report(QuiltPluginManagerImpl manager);

		/**
		 * Adds the unless clause to the error.
		 *
		 * @param error the error
		 * @param dep the dependency containing the unless
		 * @param unlessDep the rule for the unless
		 */
		protected void addUnless(QuiltDisplayedError error, ModDependency.Only dep, QuiltRuleDep unlessDep) {
			if (dep.unless() instanceof ModDependency.Only) {
				ModDependency.Only unless = (ModDependency.Only) dep.unless();
				assert unless != null;
				QuiltLoaderText versionText = VersionRangeDescriber.describe(unless.versionRange(), unless.id().id());

				QuiltRuleDepOnly unlessRule = ((QuiltRuleDepOnly) unlessDep);
				if (unlessRule.getNodesTo().isEmpty()) {
					error.appendDescription(QuiltLoaderText.translate("error.break.unless.missing",  versionText));
				} else {
					error.appendDescription(QuiltLoaderText.translate("error.break.unless.invalid",  versionText));
				}
			} else if (dep.unless() instanceof ModDependency.Any) {
				ModDependency.Any unless = (ModDependency.Any) dep.unless();
				assert unless != null;

				error.appendDescription(QuiltLoaderText.translate("error.break.unless_any"));

				for (QuiltRuleDepOnly only : ((QuiltRuleDepAny) unlessDep).options) {
					QuiltLoaderText versionText = VersionRangeDescriber.describe(only.publicDep.versionRange(), only.publicDep.id().id());
					if (only.getNodesTo().isEmpty()) {
						error.appendDescription(QuiltLoaderText.translate("error.break.unless_any.missing",  versionText));
					} else {
						error.appendDescription(QuiltLoaderText.translate("error.break.unless_any.invalid",  versionText));
					}
				}
			}
		}

		/**
		 * Adds the following mod load options as buttons to the error.
		 *
		 * @param error the error
		 * @param manager the plugin manager to get the paths to the mods
		 * @param mods an array of a collection of mods
		 */
		@SafeVarargs
		protected final void addFiles(QuiltDisplayedError error, QuiltPluginManagerImpl manager, Collection<ModLoadOption>... mods) {
			Map<Path, ModLoadOption> realPaths = new LinkedHashMap<>();

			for (Collection<ModLoadOption> modList: mods) {
				for (ModLoadOption mod : modList) {
					Object[] modDescArgs = {mod.metadata().name(), manager.describePath(mod.from())};
					error.appendDescription(QuiltLoaderText.translate("info.root_mod_loaded_from", modDescArgs));
					manager.getRealContainingFile(mod.from()).ifPresent(p -> realPaths.putIfAbsent(p, mod));
				}
			}

			for (Map.Entry<Path, ModLoadOption> entry : realPaths.entrySet()) {
				error.addFileViewButton(entry.getKey()).icon(entry.getValue().modCompleteIcon());
			}
		}

		/**
		 * Adds the issue link from the mod to the error.
		 *
		 * @param error the error
		 * @param mandatoryMod the mod
		 */
		protected void addIssueLink(QuiltDisplayedError error, ModLoadOption mandatoryMod) {
			String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
			if (issuesUrl != null) {
				error.addOpenLinkButton(QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
			}
		}

		/**
		 * Adds the mod icon to the error.
		 *
		 * @param error the error
		 * @param mandatoryMod the mod to get the icon from
		 */
		protected void setIconFromMod(QuiltDisplayedError error, ModLoadOption mandatoryMod) {
			// TODO: Only upload a ModLoadOption's icon once!
			Map<String, byte[]> images = new HashMap<>();
			for (int size : new int[]{16, 32}) {
				String iconPath = mandatoryMod.metadata().icon(size);
				if (iconPath != null && !images.containsKey(iconPath)) {
					Path path = mandatoryMod.resourceRoot().resolve(iconPath);
					try (InputStream stream = Files.newInputStream(path)) {
						images.put(iconPath, FileUtil.readAllBytes(stream));
					} catch (IOException io) {
						Log.error(LogCategory.SOLVING, "Error setting GUI icon for mod %s", mandatoryMod.metadata().name(), io);
					}
				}
			}

			if (!images.isEmpty()) {
				error.setIcon(QuiltLoaderGui.createIcon(images.values().toArray(new byte[0][])));
			}
		}

		/**
		 * Gets a description for the specified load option, including some relations and the path. It follows the format:
		 * {@code [Provided] [Required] <Mandatory|Optional> mod '<mod_id>' version '<version>' [from mod '<providing_mod_id>' (<providing_path>)]: <path>}.
		 *
		 * @param option the mod load option
		 * @param manager the plugin manager
		 *
		 * @return the mod load option description
		 */
		protected String getModReportLine(ModLoadOption option, QuiltPluginManagerImpl manager) {
			boolean provided = graph.edgesTo(option).stream().anyMatch(Provided.class::isInstance);
			boolean depended = graph.edgesTo(option).stream().anyMatch(Depends.class::isInstance);
			boolean mandatory = graph.isMandatory(option);

			StringBuilder line = new StringBuilder("- ");

			if (provided) {
				line.append("Provided");

				if (depended) {
					line.append(" and required");
				}

				if (mandatory) {
					line.append(" mandatory");
				} else {
					line.append(" optional");
				}
			} else if (depended) {
				line.append("Required");

				if (mandatory) {
					line.append(" mandatory");
				} else {
					line.append(" optional");
				}
			} else {
				if (mandatory) {
					line.append("Mandatory");
				} else {
					line.append("Optional");
				}
			}

			line.append(" mod '").append(option.id()).append("' version '").append(option.version()).append("'");

			if (provided) {
				// This cast is fine because only mod load options can be provided
				ModLoadOption providingMod = ((ModLoadOption) graph.parents.get(graph.edgesTo(option).stream().filter(Provided.class::isInstance).findFirst().get()));
				line.append(" from mod '").append(providingMod.id()).append("' (").append(manager.describePath(providingMod.from())).append(")");
			}

			return line.append(": ").append(manager.describePath(option.from())).toString();
		}

		/**
		 * Adds the version string for the dependency to the string builder, and whitespace is added around the string.
		 * If {@code start} is true, this is expected to be the start of a new line, and no leading space is added.
		 *
		 * @param report the string builder to inline the string into
		 * @param only the dependency
		 * @param breaks {@code true} if this is a breaking dependency, {@code false} if it is a required dependency
		 * @param start {@code true} if this is the start of a line, {@code false} otherwise.
		 */
		protected void addVersionString(StringBuilder report, Only only, boolean breaks, boolean start) {
			if (start) {
				// TODO: Improve version strings in the error message. Use VersionRangeDescriber?
				if (VersionRange.ANY.equals(only.versionRange())) {
					if (breaks) {
						report.append("All versions of ");
					} else {
						report.append("Any version of ");
					}
				} else {
					report.append("A version ").append(only.versionRange()).append(" of ");
				}
			} else {
				if (VersionRange.ANY.equals(only.versionRange())) {
					if (breaks) {
						report.append(" all versions of ");
					} else {
						report.append(" any version of ");
					}
				} else {
					report.append(" a version ").append(only.versionRange()).append(" of ");
				}
			}
		}

		/**
		 * Describes the unless clause for a breaking dependency. We do not handle a requiring dependency, since those algebraically resolve to a DepAny.
		 *
		 * @param dep the dependency containing the unless
		 * @param dependentName the name of the dependent mod(s)
		 * @param unlessDepRule the rule specifying the unless
		 * @param singleDependent {@code true} if there is only one dependent mod, {@code false} if there are multiple
		 *
		 * @return the lines describing the unless clause
		 */
		protected List<String> addUnlessClause(Only dep, String dependentName, QuiltRuleDep unlessDepRule, boolean singleDependent) {
			StringBuilder report = new StringBuilder();
			if (dep.unless() instanceof ModDependency.Only) {
				ModDependency.Only unless = (ModDependency.Only) dep.unless();
				assert unless != null;
				QuiltRuleDepOnly unlessRule = ((QuiltRuleDepOnly) unlessDepRule);
				if (unlessRule.getNodesTo().isEmpty()) {
					report.append("However, if");

					addVersionString(report, unless, false, false);

					report.append(unless.id()).append(" is present, ").append(dependentName).append(" do");
					if (singleDependent) {
						report.append("es");
					}
					report.append(" not break ");

					report.append(dep.id()).append(".");
				} else {
					report.append("Normally,");

					addVersionString(report, unless, false, false);

					report.append("mod ").append(unless.id()).append(" overrides this break, but it is unable to load due to another error.");
				}

				return Collections.singletonList(report.toString());
			} else if (dep.unless() instanceof ModDependency.Any) {
				ModDependency.Any unless = (ModDependency.Any) dep.unless();
				assert unless != null;
				QuiltRuleDepAny unlessRule = ((QuiltRuleDepAny) unlessDepRule);
				List<String> lines = new ArrayList<>();
				report.append("However, if any of the following are present, ");
				report.append(dependentName).append(" do");
				if (singleDependent) {
					report.append("es");
				}
				report.append(" not break.");
				lines.add(report.toString());

				for (QuiltRuleDepOnly only : unlessRule.options) {
					report = new StringBuilder("- ");
					addVersionString(report, only.publicDep, false, true);
					report.append(only.publicDep.id());

					if (only.getAllOptions().isEmpty()) {
						report.append(" which is missing.");
					} else {
						report.append(" which is unable to load due to another error.");
					}
					lines.add(report.toString());
				}
				return lines;
			}
			return Collections.emptyList();
		}
	}

	/**
	 * Reports a set of rules that cause an unknown error.
	 */
	static class UnhandledError extends SolverError {

		final Collection<Rule> rules;

		public UnhandledError(Graph graph, Collection<Rule> rules) {
			super(graph);
			this.rules = new LinkedHashSet<>(rules);
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof UnhandledError) {
				UnhandledError depDst = (UnhandledError) into;
				if (depDst.rules.containsAll(rules)) { // If the other error has all the same rules as us, we are just a subset
					return true;
				} else if (rules.containsAll(depDst.rules)) { // We have the same but more rules than the current error, add ours to it
					depDst.rules.addAll(rules);
					return true;
				}
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(
					QuiltLoaderText.translate("error.unhandled_solver")
			);
			error.appendDescription(QuiltLoaderText.translate("error.unhandled_solver.desc"));
			error.addOpenQuiltSupportButton();
			error.appendReportText("Unhandled solver error involving the following rules:");

			StringBuilder sb = new StringBuilder();
			int number = 1;
			for (Rule rule : rules) {
				error.appendDescription(QuiltLoaderText.translate("error.unhandled_solver.desc.rule_n", number, rule.getClass()));
				rule.appendRuleDescription(error::appendDescription);
				error.appendReportText("Rule " + number++ + ":");
				sb.setLength(0);
				// TODO: Rename 'fallbackErrorDescription'
				// to something like 'fallbackReportDescription'
				// and then clean up all of the implementations to be more readable.
				rule.fallbackErrorDescription(sb);
				error.appendReportText(sb.toString());
			}
			error.appendReportText("");
		}
	}

	/**
	 * Reports on a set of rules that indicate an issue with a required dependency.
	 * This can either be that it is missing, or that it is unable to load do to another error.
	 */
	static class DependsError extends SolverError {
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final QuiltRuleDepOnly depends;

		DependsError(Graph graph, ModLoadOption from, QuiltRuleDepOnly depends) {
			super(graph);
			this.from.add(from);
			this.depends = depends;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof DependsError) {
				DependsError depDst = (DependsError) into;
				if (!depends.publicDep.id().equals(depDst.depends.publicDep.id()) || !depends.publicDep.versionRange().equals(depDst.depends.publicDep.versionRange())) {
					return false;
				}
				depDst.from.addAll(from);
				return true;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {

			boolean missing = depends.getWrongOptions().isEmpty();

			// Title:
			// "BuildCraft" requires [version 1.5.1] of "Quilt Standard Libraries", which is
			// missing!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

			VersionRange range = depends.publicDep.versionRange();
			String depName = depends.publicDep.id().id();
			QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, range, depName, true, false);

			Object[] secondData = new Object[depends.getWrongOptions().size() == 1 ? 1 : 0];
			String secondKey = "error.dep.";
			if (missing) {
				secondKey += "missing";
			} else if (depends.getWrongOptions().size() > 1) {
				secondKey += "multi_mismatch";
			} else {
				secondKey += "single_mismatch";
				secondData[0] = depends.getWrongOptions().iterator().next().version().toString();
			}
			QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
			QuiltLoaderText title = QuiltLoaderText.translate("error.dep.join.title", first, second);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(error, mandatoryMod);
			addFiles(error, manager, from, depends.getValidOptions(), depends.getWrongOptions());
			addIssueLink(error, mandatoryMod);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" requires");
			addVersionString(report, depends.publicDep, false, false);
			report.append(depends.publicDep.id());// TODO
			if (!depends.getValidOptions().isEmpty()) {
				report.append(", which is unable to load due to another error!");
			} else if (missing) {
				report.append(", which is missing!");
			} else {
				// Log an error here?
				report.append(".");
			}

			error.appendReportText(report.toString(), "");

			if (!depends.publicDep.reason().isEmpty()) {
				error.appendReportText("Dependency reason: " + depends.publicDep.reason());
			}

			error.appendReportText("Requiring mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(mod, manager));
			}

			if (!depends.getValidOptions().isEmpty()) {
				error.appendReportText("");
				error.appendReportText("Satisfying mods: ");
				for (ModLoadOption mod : depends.getValidOptions()) {
					error.appendReportText(getModReportLine(mod, manager));
				}
			}

			if (!depends.getWrongOptions().isEmpty()) {
				error.appendReportText("");
				error.appendReportText("Invalid mods: ");
				for (ModLoadOption mod : depends.getWrongOptions()) {
					error.appendReportText(getModReportLine(mod, manager));
				}
			}
		}
	}

	/**
	 * Reports on a set of rules that indicate an issue with a required any dependency.
	 * It handles each only dep as either missing or unable to load.
	 */
	static class DependsAnyError extends SolverError {
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final Set<QuiltRuleDepOnly> depends;

		DependsAnyError(Graph graph, ModLoadOption from, Set<QuiltRuleDepOnly> dependsAll) {
			super(graph);
			this.from.add(from);
			this.depends = dependsAll;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof DependsAnyError) {
				DependsAnyError depDst = (DependsAnyError) into;
				Set<ModDependency.Only> ours = this.depends.stream().map(rule -> rule.publicDep).collect(Collectors.toSet());
				Set<ModDependency.Only> theirs = depDst.depends.stream().map(rule -> rule.publicDep).collect(Collectors.toSet());

				if (ours.equals(theirs)) {
					depDst.from.addAll(from);
					return true;
				}
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {

			// Title:
			// "BuildCraft" depends on any of Quilt Standard Libraries and Minecraft!
			// "BuildCraft" depends on any of Quilt Standard Libraries, Minecraft, and Third Mod!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

			Iterator<Only> depends = this.depends.stream().map(rule -> rule.publicDep).iterator();
			QuiltLoaderText allMods = QuiltLoaderText.of(depends.next().id().id());
			while (depends.hasNext()) {
				Only next = depends.next();

				if (depends.hasNext()) {
					allMods = QuiltLoaderText.translate("error.dep.join.title", allMods, next.id().id());
				} else {
					allMods = QuiltLoaderText.translate("error.dep.join.last.title", allMods, next.id().id());
				}
			}

			QuiltLoaderText title = QuiltLoaderText.translate("error.dep_any.title", rootModName, allMods);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(error, mandatoryMod);

			for (QuiltRuleDepOnly depOnly: this.depends) {
				error.appendDescription(VersionRangeDescriber.describe(depOnly.publicDep.versionRange(), depOnly.publicDep.id().id()));
				if (!depOnly.publicDep.reason().isEmpty()) {
					error.appendDescription(QuiltLoaderText.translate("error.reason.specific", depOnly.publicDep.id().id(), depOnly.publicDep.reason()));
				}

				addFiles(error, manager, depOnly.getValidOptions(), depOnly.getWrongOptions());
				error.appendDescription(QuiltLoaderText.of(""));
			}

			addFiles(error, manager, from);

			addIssueLink(error, mandatoryMod);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" depend");
			if (from.size() == 1) {
				report.append("s");
			}
			report.append(" on any of the following mods:");

			boolean skippedBreak = false;

			error.appendReportText(report.toString());
			for (QuiltRuleDepOnly breakOnly: this.depends) {
				skippedBreak = false;

				report = new StringBuilder("- ");
				addVersionString(report, breakOnly.publicDep, false, true);
				report.append(breakOnly.publicDep.id()); // TODO

				if (breakOnly.getAllOptions().isEmpty()) {
					report.append(", which is missing!");
					if (breakOnly.publicDep.reason().isEmpty()) {
						error.appendReportText(report.toString());
						skippedBreak = true;
						continue;
					}
				} else {
					report.append(":");
				}

				error.appendReportText(report.toString());
				if (!breakOnly.publicDep.reason().isEmpty()) {
					error.appendReportText("  Depend reason: " + breakOnly.publicDep.reason());
				}

				if (!breakOnly.getValidOptions().isEmpty()) {
					error.appendReportText("  Satisfying mods which cannot load: ");
					for (ModLoadOption mod : breakOnly.getValidOptions()) {
						error.appendReportText("  " + getModReportLine(mod, manager));
					}
				}

				if (!breakOnly.getWrongOptions().isEmpty()) {
					error.appendReportText("  Invalid mods: ");
					for (ModLoadOption mod : breakOnly.getWrongOptions()) {
						error.appendReportText("  " + getModReportLine(mod, manager));
					}
				}
				error.appendReportText("");
			}

			if (skippedBreak) {
				error.appendReportText("");
			}

			error.appendReportText("Requiring mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(mod, manager));
			}
		}
	}

	/**
	 * Reports on a set of rules that indicate an issue with a breaking dependency.
	 */
	static class BreaksError extends SolverError {
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final QuiltRuleBreakOnly breakage;

		BreaksError(Graph graph, ModLoadOption from, QuiltRuleBreakOnly breakage) {
			super(graph);
			this.from.add(from);
			this.breakage = breakage;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof BreaksError) {
				BreaksError depDst = (BreaksError) into;
				if (!breakage.publicDep.id().equals(depDst.breakage.publicDep.id()) || !breakage.publicDep.versionRange().equals(depDst.breakage.publicDep.versionRange())) {
					return false;
				}
				depDst.from.addAll(from);
				return true;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {
			// Title:
			// "BuildCraft" breaks with [version 1.5.1] of "Quilt Standard Libraries", but it's present!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

			QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, breakage.publicDep.versionRange(), breakage.publicDep.id().id(), false, false);

			Object[] secondData = new Object[breakage.getConflictingOptions().size() == 1 ? 1 : 0];
			String secondKey = "error.break.";
			if (breakage.getConflictingOptions().size() > 1) {
				secondKey += "multi_conflict";
			} else {
				secondKey += "single_conflict";
				secondData[0] = breakage.getConflictingOptions().iterator().next().version().toString();
			}
			QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
			QuiltLoaderText title = QuiltLoaderText.translate("error.break.join.title", first, second);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(error, mandatoryMod);

			if (!breakage.publicDep.reason().isEmpty()) {
				error.appendDescription(QuiltLoaderText.translate("error.reason", breakage.publicDep.reason()));
			}

			if (breakage.publicDep.unless() != null) {
				addUnless(error, breakage.publicDep, breakage.unless);
				// A newline after the reason was desired here, but do you think Swing loves nice things?
			}

			addFiles(error, manager, from, breakage.getConflictingOptions());
			addIssueLink(error, mandatoryMod);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" break");
			if (from.size() == 1) {
				report.append("s");
			}
			addVersionString(report, breakage.publicDep, true, false);
			report.append(breakage.publicDep.id());// TODO
			report.append(", which is present!");
			error.appendReportText(report.toString());
			if (breakage.publicDep.unless() != null) {
				addUnlessClause(breakage.publicDep, rootModName, breakage.unless, from.size() == 1).forEach(error::appendReportText);
			}
			if (!breakage.publicDep.reason().isEmpty()) {
				error.appendReportText("Breaking mod's reason: " + breakage.publicDep.reason(), "");
			}
			error.appendReportText("");

			error.appendReportText("Breaking mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(mod, manager));
			}

			error.appendReportText("", "Broken mods: ");
			for (ModLoadOption mod : breakage.getConflictingOptions()) {
				error.appendReportText(getModReportLine(mod, manager));
			}

			if (breakage.unless != null) {
				if (breakage.unless instanceof QuiltRuleDepOnly && !breakage.unless.getNodesTo().isEmpty()) {
					error.appendReportText("", "Overriding mods: ");
					for (LoadOption option : breakage.unless.getNodesTo()) {
						if (option instanceof ModLoadOption) {
							error.appendReportText(getModReportLine(((ModLoadOption) option), manager));
						} else {
							error.appendReportText("- " + option.describe().toString());
						}
					}
				} else if (breakage.unless instanceof QuiltRuleDepAny) {
					boolean added = false;
					for (QuiltRuleDepOnly only: ((QuiltRuleDepAny) breakage.unless).options) {
						for (LoadOption option : only.getNodesTo()) {
							if (!added) {
								error.appendReportText("", "Overriding mods: ");
								added = true;
							}
							if (option instanceof ModLoadOption) {
								error.appendReportText(getModReportLine(((ModLoadOption) option), manager));
							} else {
								error.appendReportText("- " + option.describe().toString());
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Reports on a set of rules that indicate an issue with a breaks all dependency.
	 */
	static class BreaksAllError extends SolverError {
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final QuiltRuleBreakAll breakage;

		BreaksAllError(Graph graph, ModLoadOption from, QuiltRuleBreakAll breakage) {
			super(graph);
			this.from.add(from);
			this.breakage = breakage;
		}

		@Override
		boolean mergeInto(SolverError into) {
			// BreaksAll are rare and there probably wont be duplicates.
			// Plus collection equals are difficult
			// We will just make sure we dont have multiple errors for the same rule

			if (into instanceof BreaksAllError) {
				BreaksAllError depDst = (BreaksAllError) into;
				return breakage == depDst.breakage;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {

			// Title:
			// "BuildCraft" breaks because all of Quilt Standard Libraries and Minecraft are present!
			// "BuildCraft" breaks because all of Quilt Standard Libraries, Minecraft, and Third Mod are present!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

			Iterator<Only> breaks = breakage.publicDep.iterator();
			QuiltLoaderText allMods = QuiltLoaderText.of(breaks.next().id().id());
			while (breaks.hasNext()) {
				Only next = breaks.next();

				if (breaks.hasNext()) {
					allMods = QuiltLoaderText.translate("error.break.join.title", allMods, next.id().id());
				} else {
					allMods = QuiltLoaderText.translate("error.break.join.last.title", allMods, next.id().id());
				}
			}

			QuiltLoaderText title = QuiltLoaderText.translate("error.breaks_all.title", rootModName, allMods);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(error, mandatoryMod);

			for (QuiltRuleBreakOnly breakOnly: breakage.options) {
				error.appendDescription(VersionRangeDescriber.describe(breakOnly.publicDep.versionRange(), breakOnly.publicDep.id().id()));
				if (!breakOnly.publicDep.reason().isEmpty()) {
					error.appendDescription(QuiltLoaderText.translate("error.reason.specific", breakOnly.publicDep.id().id(), breakOnly.publicDep.reason()));
				}

				if (breakOnly.publicDep.unless() != null) {
					// [VERSION of MOD] can override this break if present.
					// [VERSION of MOD] overrides this break, but is unable to load due to another error.
					addUnless(error, breakOnly.publicDep, breakOnly.unless);
				}

				addFiles(error, manager, breakOnly.getConflictingOptions());
				error.appendDescription(QuiltLoaderText.of(""));
			}

			
			addFiles(error, manager, from);
			
			addIssueLink(error, mandatoryMod);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" break");
			if (from.size() == 1) {
				report.append("s");
			}
			report.append(" because all of the following are present:");
			error.appendReportText(report.toString());
			for (QuiltRuleBreakOnly breakOnly: breakage.options) {
				report = new StringBuilder("- ");
				addVersionString(report, breakOnly.publicDep, false, true);
				report.append(breakOnly.publicDep.id()); // TODO
				report.append(":");
				error.appendReportText(report.toString());
				if (breakOnly.publicDep.unless() != null) {
					addUnlessClause(breakOnly.publicDep, rootModName, breakOnly.unless, from.size() == 1).forEach(line -> error.appendReportText("  " + line));
				}
				if (!breakOnly.publicDep.reason().isEmpty()) {
					error.appendReportText("  Breaking reason: " + breakOnly.publicDep.reason());
				}
	
				error.appendReportText("  Matching mods: ");
				for (ModLoadOption mod : breakOnly.getConflictingOptions()) {
					error.appendReportText("  " + getModReportLine(mod, manager));
				}
	
				if (breakOnly.unless != null) {
					if (breakOnly.unless instanceof QuiltRuleDepOnly && !breakOnly.unless.getNodesTo().isEmpty()) {
						error.appendReportText("  Overriding mods: ");
						for (LoadOption option : breakOnly.unless.getNodesTo()) {
							if (option instanceof ModLoadOption) {
								error.appendReportText("  " + getModReportLine(((ModLoadOption) option), manager));
							} else {
								error.appendReportText("  - " + option.describe().toString());
							}
						}
					} else if (breakOnly.unless instanceof QuiltRuleDepAny) {
						boolean added = false;
						for (QuiltRuleDepOnly only: ((QuiltRuleDepAny) breakOnly.unless).options) {
							for (LoadOption option : only.getNodesTo()) {
								if (!added) {
									error.appendReportText("  Overriding mods: ");
									added = true;
								}
								if (option instanceof ModLoadOption) {
									error.appendReportText("  " + getModReportLine(((ModLoadOption) option), manager));
								} else {
									error.appendReportText("  - " + option.describe().toString());
								}
							}
						}
					}
				}
				error.appendReportText("");
			}

			error.appendReportText("Breaking mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(mod, manager));
			}
		}
	}

	/**
	 * Reports on a set of rules that indicate multiple mods provide the same id.
	 */
	static class DuplicatesError extends SolverError {
		final String id;
		final Set<LoadOption> duplicates = new LinkedHashSet<>();

		DuplicatesError(Graph graph, String id, Collection<LoadOption> duplicates) {
			super(graph);
			this.id = id;
			this.duplicates.addAll(duplicates);
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof DuplicatesError) {
				DuplicatesError depDst = (DuplicatesError) into;
				if (!this.id.equals(depDst.id)) {
					return false;
				}
				depDst.duplicates.addAll(duplicates);
				return true;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {
			// Step 1: Find the set of shared ids
			List<ModLoadOption> mandatoryMods = new ArrayList<>();
			Set<String> commonIds = new LinkedHashSet<>();
			for (LoadOption option : duplicates) {
				if (graph.isMandatory(option)) {
					mandatoryMods.add((ModLoadOption) option);
				}
				commonIds.add(
						((ModLoadOption) option).id()
				);
			}

			if (mandatoryMods.isEmpty()) {
				// So this means there's an OptionalModIdDefintion with only
				// DisabledModIdDefinitions as roots
				// that means this isn't a duplicate mandatory mods error!
				return;
			}

			ModLoadOption firstMandatory = mandatoryMods.get(0);
			String bestName = firstMandatory.metadata().name();

			// Title:
			// Duplicate mod: "BuildCraft"

			// Description:
			// - "buildcraft-all-9.0.0.jar"
			// - "buildcraft-all-9.0.1.jar"
			// Remove all but one.

			// With buttons to view each mod individually

			QuiltLoaderText title = QuiltLoaderText.translate("error.duplicate_mandatory", bestName);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);
			error.appendReportText("Duplicate mandatory mod ids " + commonIds);
			setIconFromMod(error, firstMandatory);

			for (LoadOption loadOption : duplicates) {
				if (loadOption instanceof ModLoadOption) {
					ModLoadOption option = ((ModLoadOption) loadOption);
					String path = manager.describePath(option.from());
					Optional<Path> container = manager.getRealContainingFile(option.from());

					// Just in case
					if (option instanceof ProvidedModOption) {
						error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.mod.provided", path));
					} else {
						error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.mod", path));
					}

					container.ifPresent(value -> error.addFileViewButton(QuiltLoaderText.translate("button.view_file", value.getFileName()), value)
							.icon(option.modCompleteIcon()));

					error.appendReportText(getModReportLine(option, manager));
				} else {
					error.appendDescription(QuiltLoaderText.translate("error.unhandled_mod_file.title", loadOption.describe()));
					error.appendReportText("- Unknown load option: " + loadOption.describe());
				}
			}

			error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.desc"));
		}
	}
}
