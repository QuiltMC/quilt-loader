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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class SolverErrorHelper {
	private final QuiltPluginManagerImpl manager;
	private final List<SolverError> errors = new ArrayList<>();

	private final Graph graph = new Graph();

	SolverErrorHelper(QuiltPluginManagerImpl manager) {
		this.manager = manager;
	}

	void reportErrors() {
		printGraph(graph);
		for (SolverError error : errors) {
			error.report(manager);
		}
	}

	private void addError(SolverError error) {
		for (SolverError e2 : errors) {
			if (error.mergeInto(e2)) {
				return;
			}
		}
		errors.add(error);
	}

	void reportSolverError(Collection<Rule> rules) {
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
						graph.addLink(load, new DependsAny(depends));
					} else {
						graph.addLink(load, new MissingAny(
								((QuiltRuleDepAny) rule).publicDep
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
				System.out.print("Unknown rule: " + rule.getClass().getSimpleName() + " -> ");
				rule.appendRuleDescription(System.out::println);
			}
		}

		graph.clean();

		// System.out.println(graph);

//		graph.remove(null);
//		boolean modified = false;
//		do {
//			modified = false;
//			Set<LoadOption> allChildren = graph.values()
//					.stream()
//					.flatMap(Collection::stream)
//					.flatMap(Link::children)
//					.collect(Collectors.toSet());
//
//			Set<Option> noLinks = graph.keySet()
//					.stream()
//					.filter(option -> !option.mandatory)
//					.collect(Collectors.toCollection(HashSet::new));
//
//			noLinks.removeAll(allChildren);
//
//			for (Option o : noLinks) {
//				graph.remove(o);
//				modified = true;
//			}
//		} while (modified);

//		printGraph(graph);

		if (!reportNewError(graph))
			addError(new UnhandledError(graph, rules));
	}

	boolean reportNewError(Graph graph) {
		try {
			boolean added = false;
			for (LoadOption option : graph.nodes) {
				for (Link link : graph.edges(option)) {
					if (link instanceof Breaks) {
						Breaks breaks = ((Breaks) link);
						ModLoadOption from = (ModLoadOption) option;
						Set<ModLoadOption> allBreakingOptions = new LinkedHashSet<>();
						allBreakingOptions.addAll(breaks.rule.getConflictingOptions());
						this.addError(new BreakageError(graph, breaks.rule.publicDep, from, allBreakingOptions, breaks.rule.unless));
						added = true;
					} else if (link instanceof BreaksAll) {
						BreaksAll breaksAll = ((BreaksAll) link);
						ModLoadOption from = (ModLoadOption) option;
						Set<ModLoadOption> allBreakingOptions = new LinkedHashSet<>();
						for (QuiltRuleBreakOnly only : breaksAll.rule.options) {
							allBreakingOptions.addAll(only.getConflictingOptions());
						}

						for (ModDependency.Only only : breaksAll.rule.publicDep) {
							ModDependencyIdentifier modOn = only.id();
							VersionRange versionsOn = only.versionRange();
							String reason = only.reason();
							// TODO: BreakageAll?
							// this.addError(new BreakageError(modOn, versionsOn, from, allBreakingOptions, reason));
						}
					} else if (link instanceof Missing) {
						Missing missing = ((Missing) link);
						ModLoadOption from = (ModLoadOption) option;
						this.addError(new MissingDependencyError(graph, missing.missing, from, new LinkedHashSet<>(missing.rule.getValidOptions()), new LinkedHashSet<>(missing.rule.getWrongOptions()), missing.rule.unless));
						added = true;
					} else if (link instanceof Depends) {
						Depends depends = ((Depends) link);
						ModLoadOption from = (ModLoadOption) option;
						this.addError(new MissingDependencyError(graph, depends.rule.publicDep, from, new LinkedHashSet<>(depends.rule.getValidOptions()), new LinkedHashSet<>(depends.rule.getWrongOptions()), depends.rule.unless));
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
			e.printStackTrace();
			return false;
		}
	}

	static void printGraph(Graph graph) {
		System.out.println("digraph G {");
		System.out.printf("\troot[style=invis];\n");
		System.out.printf("\tsubgraph cluster_root {\n");
		graph.nodes
				.stream()
				.filter(o -> isMandatory(graph, o))
				.forEach(option -> {
					System.out.printf("\t\t%s[label=\"%s\", shape=\"Mdiamond\"];\n", option.hashCode(), label(option));
					// System.out.printf("\t\troot->%s[style=invis];\n", option.hashCode());
				});
		System.out.printf("\t\tstyle=invis;\n");
		System.out.printf("\t}\n");
		graph.nodes
				.forEach(option -> {
					if (isMandatory(graph, option))
						System.out.printf("\troot->%s[style=invis];\n", option.hashCode());
					else
						System.out.printf("\t%s[label=\"%s\", shape=\"rectangle\"];\n", option.hashCode(), label(option));
				});

		graph.nodes.forEach(node -> graph.edges(node).forEach(link -> link.dotGraphEdge(node)));
		graph.nodes.stream()
				.flatMap(node -> graph.edgesTo(node).stream().filter(RootLink.class::isInstance))
				.distinct()
				.forEach(link -> link.dotGraphEdge(null));
		System.out.println("}");
		System.out.println();
	}

	static boolean isMandatory(Graph graph, LoadOption option) {
		boolean mandatory = graph.edgesTo(option).stream().anyMatch(Mandatory.class::isInstance);

		mandatory |= graph.edgesTo(option)
				.stream()
				.filter(Provided.class::isInstance)
				.map(graph.parents::get)
				.filter(Objects::nonNull)
				.map(graph::edgesTo)
				.flatMap(Collection::stream)
				.anyMatch(Mandatory.class::isInstance);

		return mandatory;
	}

	public static String label(LoadOption option) {
		if (option instanceof ModLoadOption) {
			return ((ModLoadOption) option).id() + "\\n" + ((ModLoadOption) option).version();
		}

		return option.toString();
	}

	static class Graph {
		final Set<LoadOption> nodes = new LinkedHashSet<>();
		final Map<LoadOption, Set<Link>> edges = new LinkedHashMap<>();
		// TODO: Pair<Option, Link>?
		final Map<LoadOption, Set<Link>> reverseEdges = new LinkedHashMap<>();

		final Map<Link, LoadOption> parents = new LinkedHashMap<>();

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

		public Set<Link> edges(LoadOption from) {
			return edges.getOrDefault(from, new LinkedHashSet<>());
		}

		public Set<Link> edgesTo(LoadOption to) {
			return reverseEdges.getOrDefault(to, new LinkedHashSet<>());
		}

		public void clean() {
			boolean modified = false;
			do {
				modified = false;
//				Set<LoadOption> allChildren = new HashSet<>(parents.values());

				Set<LoadOption> noLinks = this.parents.values()
						.stream()
//						.filter(option -> !isMandatory(this, option)) // Redundant?
						.filter(option -> this.edgesTo(option).isEmpty())
						.collect(Collectors.toCollection(HashSet::new));

				for (LoadOption o : noLinks) {
					nodes.remove(o);
					Set<Link> removedLinks = this.edges.remove(o);
					for (Link link: removedLinks) {
						this.parents.remove(link);
					}
					modified = true;
				}
			} while (modified);
		}
	}

	interface Link {
		Stream<LoadOption> children();

		default void dotGraphEdge(LoadOption from) {
			this.children().forEach(child -> {
				System.out.printf("\t%s->%s [label=\"%s\"];\n", from.hashCode(), child.hashCode(), this.getClass().getSimpleName());
			});
		}
	}

	interface RootLink extends Link {
	}

	static class Breaks implements Link {
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
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
			if(rule.unless != null) {
				for (LoadOption option : rule.unless.getNodesTo()) {
					System.out.printf("\t%s->%s [label=\"Unless\", color=blue];\n", from.hashCode(), option.hashCode());
				}
			}
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

	static class BreaksAll implements Link {
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
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
			System.out.printf("\t%s [label=\"All Of\", shape=\"invtriangle\", color=red];\n", breaks.hashCode());
			for (LoadOption b : breaks) {
				System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", breaks.hashCode(), b.hashCode());
			}
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

	static class Depends implements Link {
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

	static class DependsAny implements Link {
		@NotNull
		final Collection<LoadOption> depends;

		public DependsAny(@NotNull Collection<LoadOption> depends) {
			this.depends = depends;
		}

		public Stream<LoadOption> children() {
			return depends.stream();
		}

		@Override
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), depends.hashCode());
			System.out.printf("\t%s [label=\"Any Of\", shape=\"invtriangle\"];\n", depends.hashCode());
			for (LoadOption b : depends) {
				System.out.printf("\t%s->%s [label=\"Depends\"];\n", depends.hashCode(), b.hashCode());
			}
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

	static class Missing implements Link {
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
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
			System.out.printf("\t%s [label=\"Missing: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.id());
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

	static class MissingAny implements Link {
		@NotNull
		final ModDependency.Any missing;

		public MissingAny(@NotNull ModDependency.Any missing) {
			this.missing = missing;
		}

		public Stream<LoadOption> children() {
			return Stream.empty();
		}

		@Override
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
			System.out.printf("\t%s [label=\"Missing any of: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.stream().map(ModDependency.Only::id).map(ModDependencyIdentifier::toString).collect(Collectors.joining(", ")));
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
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s[label=\"%s\"];\n", id, id);
			for (LoadOption option : options) {
				System.out.printf("\t%s->%s [label=\"Duplicates\", style=\"dashed\"];\n", option.hashCode(), id);
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
		public void dotGraphEdge(LoadOption from) {
			// System.out.printf("\t%s->%s [label=\"Provides\", style=\"dashed\"];\n", from.hashCode(), option.hashCode());
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
		public void dotGraphEdge(LoadOption from) {
			System.out.printf("\t%s->%s [label=\"Provides\", style=\"dashed\"];\n", from.hashCode(), provides.hashCode());
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

	private static void setIconFromMod(QuiltPluginManagerImpl manager, ModLoadOption mandatoryMod, QuiltDisplayedError error) {
		// TODO: Only upload a ModLoadOption's icon once!
		Map<String, byte[]> images = new HashMap<>();
		for (int size : new int[]{16, 32}) {
			String iconPath = mandatoryMod.metadata().icon(size);
			if (iconPath != null && !images.containsKey(iconPath)) {
				Path path = mandatoryMod.resourceRoot().resolve(iconPath);
				try (InputStream stream = Files.newInputStream(path)) {
					images.put(iconPath, FileUtil.readAllBytes(stream));
				} catch (IOException io) {
					// TODO: Warn about this somewhere!
					io.printStackTrace();
				}
			}
		}

		if (!images.isEmpty()) {
			error.setIcon(QuiltLoaderGui.createIcon(images.values().toArray(new byte[0][])));
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

		abstract void report(QuiltPluginManagerImpl manager);

		protected String getModReportLine(QuiltPluginManagerImpl manager, ModLoadOption option) {
			String path = manager.describePath(option.from());
			// TODO: Depended or Required
			// [Provided] [Depended] <Mandatory|Optional>

			boolean provided = graph.edgesTo(option).stream().anyMatch(Provided.class::isInstance);
			boolean depended = graph.edgesTo(option).stream().anyMatch(Depends.class::isInstance);
			boolean mandatory = isMandatory(graph, option);

			StringBuilder line = new StringBuilder("- ");

			if (provided) {
				line.append("Provided");

				if (depended) {
					line.append(" and depended");
				}

				if (mandatory) {
					line.append(" mandatory");
				} else {
					line.append(" optional");
				}
			} else if (depended) {
				line.append("Depended");

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

			return line.append(" mod '").append(option.id()).append("' version '").append(option.version()).append("': ").append(path).toString();
		}

		protected void addVersionString(ModDependency.Only unless, StringBuilder report, boolean breaks) {
			if (VersionRange.ANY.equals(unless.versionRange())) {
				if (breaks) {
					report.append(" all versions of ");
				} else {
					report.append(" any version of ");
				}
			} else {
				report.append(" a version ").append(unless.versionRange()).append(" of ");
			}
		}

		protected String addUnlessClause(ModDependency.Only dep, String rootModName, Set<ModLoadOption> from, QuiltRuleDep unlessDep, boolean breaks) {
			StringBuilder report = new StringBuilder();
			if (dep.unless() instanceof ModDependency.Only) {
				ModDependency.Only unless = (ModDependency.Only) dep.unless();
				assert unless != null;
				QuiltRuleDepOnly unlessRule = ((QuiltRuleDepOnly) unlessDep);
				if (unlessRule.getNodesTo().isEmpty()) {
					report.append("However, if");

					addVersionString(unless, report, false);

					report.append(unless.id()).append(" is present, ").append(rootModName).append(" do");
					if (from.size() == 1) {
						report.append("es");
					}
					report.append(" not ");
					if (breaks) {
						report.append("break ");
					} else {
						report.append("require ");
					}

					report.append(dep.id()).append(".");
				}else {
					report.append("Normally,");

					addVersionString(unless, report, false);

					report.append("of mod ").append(unless.id()).append(" overrides this ");
					if (breaks) {
						report.append("break");
					} else {
						report.append("requirement");
					}
					report.append(", but it is unable to load due to another error.");
				}
			}
			// TODO support Any option
			return report.toString();
		}

		@SafeVarargs
		protected final void addFiles(QuiltPluginManagerImpl manager, QuiltDisplayedError error, Collection<ModLoadOption>... mods) {
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

		protected void addIssueLink(ModLoadOption mandatoryMod, QuiltDisplayedError error) {
			String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
			if (issuesUrl != null) {
				error.addOpenLinkButton(QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
			}
		}
	}

	static class UnhandledError extends SolverError {

		final Collection<Rule> rules;

		public UnhandledError(Graph graph, Collection<Rule> rules) {
			super(graph);
			this.rules = rules;
		}

		@Override
		boolean mergeInto(SolverError into) {
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
			// Todo: Maybe print the dot graph with a flag?
		}
	}

	static class MissingDependencyError extends SolverError {
		final ModDependency.Only dep;
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final Set<ModLoadOption> allInvalidOptions;
		final Set<ModLoadOption> allValidOptions;
		final QuiltRuleDep unlessDep;

		MissingDependencyError(Graph graph, ModDependency.Only dep, ModLoadOption from, Set<ModLoadOption> allValidOptions, Set<ModLoadOption> allInvalidOptions, QuiltRuleDep unlessDep) {
			super(graph);
			this.dep = dep;
			this.from.add(from);
			this.allInvalidOptions = allInvalidOptions;
			this.allValidOptions = allValidOptions;
			this.unlessDep = unlessDep;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof MissingDependencyError) {
				MissingDependencyError depDst = (MissingDependencyError) into;
				if (!dep.id().equals(depDst.dep.id()) || !dep.versionRange().equals(depDst.dep.versionRange())) {
					return false;
				}
				depDst.from.addAll(from);
				return true;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {

			boolean transitive = false;
			boolean missing = allInvalidOptions.isEmpty();

			// Title:
			// "BuildCraft" requires [version 1.5.1] of "Quilt Standard Libraries", which is
			// missing!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods" : mandatoryMod.metadata().name();

			QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, dep.versionRange(), dep.id().id(), transitive);

			Object[] secondData = new Object[allInvalidOptions.size() == 1 ? 1 : 0];
			String secondKey = "error.dep.";
			if (missing) {
				secondKey += "missing";
			} else if (allInvalidOptions.size() > 1) {
				secondKey += "multi_mismatch";
			} else {
				secondKey += "single_mismatch";
				secondData[0] = allInvalidOptions.iterator().next().version().toString();
			}
			QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
			QuiltLoaderText title = QuiltLoaderText.translate("error.dep.join.title", first, second);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(manager, mandatoryMod, error);
			addFiles(manager, error, from, allValidOptions);
			addIssueLink(mandatoryMod, error);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" requires");
			addVersionString(dep, report, false);
			report.append(dep.id());// TODO
			if (!allValidOptions.isEmpty()) {
				report.append(", which is unable to load due to another error!");
			} else if (missing) {
				report.append(", which is missing!");
			} else {
				// Log an error here?
				report.append(".");
			}

			error.appendReportText(report.toString(), "");

			if (dep.unless() != null) {
				error.appendReportText(addUnlessClause(dep, rootModName, from, unlessDep, false), "");
			}

			error.appendReportText("Requiring mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(manager, mod));
//				error.appendReportText("- " + manager.describePath(mod.from()));
			}

			if (!allValidOptions.isEmpty()) {
				error.appendReportText("");
				error.appendReportText("Satisfying mods: ");
				for (ModLoadOption mod : allValidOptions) {
					error.appendReportText(getModReportLine(manager, mod));
				}
			}

			if (!allInvalidOptions.isEmpty()) {
				error.appendReportText("");
				error.appendReportText("Invalid mods: ");
				for (ModLoadOption mod : allInvalidOptions) {
					error.appendReportText(getModReportLine(manager, mod));
				}
			}
		}
	}

	static class BreakageError extends SolverError {
		// final ModDependencyIdentifier modOn;
		// final VersionRange versionsOn;
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final Set<ModLoadOption> allBreakingOptions;
		// final String reason;
		final ModDependency.Only dep;
		final QuiltRuleDep unlessDep;

		BreakageError(Graph graph, ModDependency.Only dep, ModLoadOption from, Set<ModLoadOption> allBreakingOptions, QuiltRuleDep unlessDep) {
			super(graph);
			this.dep = dep;
			// this.versionsOn = versionsOn;
			this.from.add(from);
			this.allBreakingOptions = allBreakingOptions;
			this.unlessDep = unlessDep;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof BreakageError) {
				BreakageError depDst = (BreakageError) into;
				if (!dep.id().equals(depDst.dep.id()) || !dep.versionRange().equals(depDst.dep.versionRange())) {
					return false;
				}
				depDst.from.addAll(from);
				return true;
			}
			return false;
		}

		@Override
		void report(QuiltPluginManagerImpl manager) {

			boolean transitive = false;

			// Title:
			// "BuildCraft" breaks with [version 1.5.1] of "Quilt Standard Libraries", but it's present!

			// Description:
			// BuildCraft is loaded from '<mods>/buildcraft-9.0.0.jar'
			ModLoadOption mandatoryMod = from.iterator().next();
			String rootModName = from.size() > 1 ? from.size() + " mods [" + from.stream().map(ModLoadOption::metadata).map(ModMetadataExt::name).collect(Collectors.joining(", ")) + "]" : mandatoryMod.metadata().name();

			QuiltLoaderText first = VersionRangeDescriber.describe(
					rootModName, dep.versionRange(), dep.id().id(), false, transitive
			);

			Object[] secondData = new Object[allBreakingOptions.size() == 1 ? 1 : 0];
			String secondKey = "error.break.";
			if (allBreakingOptions.size() > 1) {
				secondKey += "multi_conflict";
			} else {
				secondKey += "single_conflict";
				secondData[0] = allBreakingOptions.iterator().next().version().toString();
			}
			QuiltLoaderText second = QuiltLoaderText.translate(secondKey + ".title", secondData);
			QuiltLoaderText title = QuiltLoaderText.translate("error.break.join.title", first, second);
			QuiltDisplayedError error = manager.theQuiltPluginContext.reportError(title);

			setIconFromMod(manager, mandatoryMod, error);

			if (!dep.reason().isEmpty()) {
				error.appendDescription(QuiltLoaderText.translate("error.reason", dep.reason()));
				// A newline after the reason was desired here, but do you think Swing loves nice things?
			}

			addFiles(manager, error, from, allBreakingOptions);
			addIssueLink(mandatoryMod, error);

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" break");
			if (from.size() == 1) {
				report.append("s");
			}
			addVersionString(dep, report, true);
			report.append(dep.id());// TODO
			report.append(", which is present!");
			error.appendReportText(report.toString());
			if (dep.unless() != null) {
				error.appendReportText(addUnlessClause(dep, rootModName, from, unlessDep, true));
			}
			if (!dep.reason().isEmpty()) {
				error.appendReportText("Breaking mod's reason: " + dep.reason(), "");
			}
			error.appendReportText("");

			error.appendReportText("Breaking mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText(getModReportLine(manager, mod));
			}

			error.appendReportText("", "Broken mods: ");
			for (ModLoadOption mod : allBreakingOptions) {
				error.appendReportText(getModReportLine(manager, mod));
			}

			if (unlessDep != null && !unlessDep.getNodesTo().isEmpty()) {
				error.appendReportText("", "Overriding mods: ");
				for (LoadOption option : unlessDep.getNodesTo()) {
					if (option instanceof ModLoadOption) {
						error.appendReportText(getModReportLine(manager, ((ModLoadOption) option)));
					} else {
						error.appendReportText("- " + option.describe().toString());
					}
				}
			}
		}
	}

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
				if (isMandatory(graph, option)) {
					mandatoryMods.add((ModLoadOption) option);
				}
				// } else if (option.option instanceof ProvidedModOption) {
				// 	// TODO: Get the graph here
				// 	// TEMP: assume its manditory
//				mandatoryMods.add(((ProvidedModOption) option));
				// }
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
			setIconFromMod(manager, firstMandatory, error);

			for (LoadOption loadOption : duplicates) {
				// TODO: is this safe?
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

				error.appendReportText(getModReportLine(manager, option));
			}

			error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.desc"));
		}
	}
}
