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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.plugin.quilt.DisabledModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.MandatoryModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.OptionalModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.ProvidedModOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakAll;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreakOnly;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepAny;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDepOnly;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class SolverErrorHelper {

	private static final Option.Root ROOT = new Option.Root();

	private final QuiltPluginManagerImpl manager;
	private final List<SolverError> errors = new ArrayList<>();

	SolverErrorHelper(QuiltPluginManagerImpl manager) {
		this.manager = manager;
	}

	void reportErrors() {
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

		List<RuleLink> links = new ArrayList<>();
		Map<LoadOption, OptionLink> option2Link = new HashMap<>();

		for (Rule rule : rules) {
			RuleLink ruleLink = new RuleLink(rule);
			links.add(ruleLink);

			for (LoadOption from : rule.getNodesFrom()) {
				from = getTarget(from);
				OptionLink optionLink = option2Link.computeIfAbsent(from, OptionLink::new);
				ruleLink.from.add(optionLink);
				optionLink.to.add(ruleLink);
			}

			for (LoadOption from : rule.getNodesTo()) {
				from = getTarget(from);
				OptionLink optionLink = option2Link.computeIfAbsent(from, OptionLink::new);
				ruleLink.to.add(optionLink);
				optionLink.from.add(ruleLink);
			}
		}

		List<RuleLink> rootRules = new ArrayList<>();
		for (RuleLink link : links) {
			if (link.from.isEmpty()) {
				rootRules.add(link);
			}
		}

		Map<Option, Collection<Link>> graph = new HashMap<>();
		Map<LoadOption, Option> options = new HashMap<>();

		for (Rule rule : rules) {
			for (LoadOption option: rule.getNodesTo()) {
				options.compute(option, (loadOption, currentOption) -> {
					if (currentOption == null) {
						return new Option(loadOption, rule instanceof MandatoryModIdDefinition);
					}
					assert currentOption.option == loadOption;
					return new Option(loadOption, currentOption.mandatory | rule instanceof MandatoryModIdDefinition);
				});
			}
		}

		for (Rule rule : rules) {
			if (rule instanceof QuiltRuleBreakAll) {
				Set<Option> breaks = rule.getNodesTo()
					.stream()
					.flatMap(loadOption -> rules.stream().filter(r -> r.getNodesFrom().contains(loadOption)))
					.flatMap(r -> r.getNodesTo().stream())
					.map(options::get)
					.collect(Collectors.toSet());

				for (LoadOption load: rule.getNodesFrom())
					graph.computeIfAbsent(options.get(load), option -> new ArrayList<>())
						.add(new BreaksAll(breaks, (QuiltRuleBreakAll) rule));
			} else if (rule instanceof QuiltRuleBreakOnly) {
				Option breaks = rule.getNodesTo().stream()
					.map(options::get)
					.findFirst()
					.get();

				for (LoadOption load: rule.getNodesFrom())
					graph.computeIfAbsent(options.get(load), option -> new ArrayList<>())
						.add(new Breaks(breaks, ((QuiltRuleBreakOnly) rule)));
			} else if (rule instanceof QuiltRuleDepAny) {
				Set<Option> depends = rule.getNodesTo()
					.stream()
					.flatMap(loadOption -> rules.stream().filter(r -> r.getNodesFrom().contains(loadOption)))
					.flatMap(r -> r.getNodesTo().stream())
					.peek(System.out::println)
					.map(options::get)
					.peek(System.out::println)
					.collect(Collectors.toSet());

				for (LoadOption load: rule.getNodesFrom()) {
					Collection<Link> l = graph.computeIfAbsent(options.get(load), option -> new ArrayList<>());

					if (!depends.isEmpty())
						l.add(new DependsAny(depends));
					else {
						l.add(new MissingAny(
								((QuiltRuleDepAny) rule).publicDep
						));
					}
				}
			} else if (rule instanceof QuiltRuleDepOnly) {
				Optional<Option> depends = rule.getNodesTo().stream()
					.map(options::get)
					.findFirst();

				for (LoadOption load: rule.getNodesFrom()) {
					Collection<Link> l = graph.computeIfAbsent(options.get(load), option -> new ArrayList<>());

					if (depends.isPresent())
						l.add(new Depends(depends.get()));
					else {
						l.add(new Missing(
								((QuiltRuleDepOnly) rule).publicDep
						));
					}
				}
			} else if (rule instanceof MandatoryModIdDefinition) {
				for (LoadOption load: rule.getNodesTo()) {
					assert options.get(load).mandatory;
					graph.computeIfAbsent(ROOT, option -> new ArrayList<>())
						.add(new Manditory(options.get(load)));
				}
			} else if (rule instanceof OptionalModIdDefinition) {
				OptionalModIdDefinition definition = (OptionalModIdDefinition) rule;

				graph.computeIfAbsent(ROOT, option -> new ArrayList<>())
					.add(
						new Duplicates(
							rule.getNodesTo()
								.stream()
								.map(options::get)
								.collect(Collectors.toList()),
							definition.getModId()
						)
					);

				for (LoadOption loadOption : rule.getNodesTo()) {
					if (loadOption instanceof ProvidedModOption) {
						ProvidedModOption provided = (ProvidedModOption) loadOption;
						graph.computeIfAbsent(options.get(provided.getTarget()), option -> new ArrayList<>())
							.add(new Provided(options.get(provided)));
					}
				}
			} else {
				System.out.print("Unknown rule: " + rule.getClass().getSimpleName() + " -> ");
				rule.appendRuleDescription(System.out::println);
			}
		}

		// System.out.println(graph);

		graph.remove(null);
		boolean modified = false;
		do {
			modified = false;
			Set<Option> allChildren = graph.values()
				.stream()
				.flatMap(Collection::stream)
				.flatMap(Link::children)
				.collect(Collectors.toSet());

			Set<Option> noLinks = graph.keySet()
				.stream()
				.filter(option -> !option.mandatory)
				.collect(Collectors.toCollection(HashSet::new));

			noLinks.removeAll(allChildren);

			for (Option o : noLinks) {
				graph.remove(o);
				modified = true;
			}
		}	while(modified);

		printGraph(graph);

		reportNewError(graph);

		addError(new UnhandledError(rules));

		if (reportKnownSolverError(rootRules)) {
			return;
		}
	}

	void reportNewError(Map<Option, Collection<Link>> graph) {
		for (Option option: graph.keySet()) {
			for (Link link: graph.get(option)) {
				if (link instanceof Breaks) {
					Breaks breaks = ((Breaks) link);
					ModLoadOption from = (ModLoadOption) option.option;
					Set<ModLoadOption> allBreakingOptions = new LinkedHashSet<>();
					allBreakingOptions.addAll(breaks.rule.getConflictingOptions());
					this.addError(new BreakageError(breaks.rule.publicDep, from, allBreakingOptions));
				} else if (link instanceof BreaksAll) {
					BreaksAll breaksAll = ((BreaksAll) link);
					ModLoadOption from = (ModLoadOption) option.option;
					Set<ModLoadOption> allBreakingOptions = new LinkedHashSet<>();
					for (QuiltRuleBreakOnly only : breaksAll.rule.options) {
						allBreakingOptions.addAll(only.getConflictingOptions());
					}

					for (ModDependency.Only only: breaksAll.rule.publicDep) {
						ModDependencyIdentifier modOn = only.id();
						VersionRange versionsOn = only.versionRange();
						String reason = only.reason();
						// TODO: BreakageAll?
						// this.addError(new BreakageError(modOn, versionsOn, from, allBreakingOptions, reason));
					}
				} else if (link instanceof Duplicates) {
					Duplicates duplicates = (Duplicates) link;
					this.addError(new DuplicatesError(duplicates.id, duplicates.options));
				}
			}
		}
	}

	static void printGraph(Map<Option, Collection<Link>> graph) {
		System.out.println("digraph G {");
		System.out.printf("\troot[style=invis];\n");
		System.out.printf("\tsubgraph cluster_root {\n");
		graph.entrySet()
			.stream()
			.flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), entry.getValue().stream().flatMap(Link::children)))
			.distinct()
			.filter(o -> o.mandatory)
			.filter(o -> !ROOT.equals(o))
			.forEach(option -> {
				System.out.printf("\t\t%s[label=\"%s\", shape=\"Mdiamond\"];\n", option.hashCode(), option.label());
				// System.out.printf("\t\troot->%s[style=invis];\n", option.hashCode());
			});
		System.out.printf("\t\tstyle=invis;\n");
		System.out.printf("\t}\n");
		graph.entrySet()
			.stream()
			.flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), entry.getValue().stream().flatMap(Link::children)))
			.distinct()
			.filter(o -> !ROOT.equals(o))
			.forEach(option -> {
				if (option.mandatory)
					System.out.printf("\troot->%s[style=invis];\n", option.hashCode());
				else
					System.out.printf("\t%s[label=\"%s\", shape=\"rectangle\"];\n", option.hashCode(), option.label());
			});

		graph.entrySet().stream()
			.forEach(entry -> entry.getValue().forEach(link -> link.dotGraphEdge(entry.getKey())));
		System.out.println("}");
		System.out.println();
	}

	static class Option {
		final LoadOption option;
		boolean mandatory;

		public Option(LoadOption option, boolean mandatory) {
			this.option = option;
			this.mandatory = mandatory;
		}

		public String label() {
			if (option instanceof ModLoadOption) {
				return ((ModLoadOption) option).id();
			} else if (option instanceof ProvidedModOption) {
				return ((ProvidedModOption) option).id();
			} 

			return this.toString();
		}

        @Override
        public String toString() {
            return "{option=" + option.describe() + ", mandatory=" + mandatory + "}";
        }

		static class Root extends Option {
			public Root() {
				super(null, true);
			}

			@Override
			public String label() {
				return "ROOT";
			}
			
			@Override
			public String toString() {
				return "ROOT";
			}
		}
	}

	interface Link {
		Stream<Option> children();

		default void dotGraphEdge(Option from) {
			this.children().forEach(child -> {
				System.out.printf("\t%s->%s [label=\"%s\"];\n", from.hashCode(), child.hashCode(), this.getClass().getSimpleName());
			});
		}
	};

	static class Breaks implements Link {
		QuiltRuleBreakOnly rule;
		Option breaks;

		public Breaks(Option breaks, QuiltRuleBreakOnly rule) {
			this.breaks = breaks;
			this.rule = rule;
		}

		public Stream<Option> children() {
			return Stream.of(breaks);
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
		}

		@Override
        public String toString() {
            return "Breaks=" + breaks;
        }
	}

	static class BreaksAll implements Link {
		Collection<Option> breaks;
		QuiltRuleBreakAll rule;

		public BreaksAll(Collection<Option> breaks, QuiltRuleBreakAll rule) {
			this.breaks = breaks;
			this.rule = rule;
		}

		public Stream<Option> children() {
			return breaks.stream();
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", from.hashCode(), breaks.hashCode());
			System.out.printf("\t%s [label=\"All Of\", shape=\"invtriangle\", color=red];\n", breaks.hashCode());
			for (Option b: breaks) {
				System.out.printf("\t%s->%s [label=\"Breaks\", dir=both, color=red];\n", breaks.hashCode(), b.hashCode());
			}
		}

        @Override
        public String toString() {
            return "BreaksAll=" + breaks;
        }
	}

	static class Depends implements Link {
		Option depends;

		public Depends(Option depends) {
			this.depends = depends;
		}

		public Stream<Option> children() {
			return Stream.of(depends);
		}

		@Override
        public String toString() {
            return "Depends=" + depends;
        }
	}

	static class DependsAny implements Link {
		Collection<Option> depends;

		public DependsAny(Collection<Option> depends) {
			this.depends = depends;
		}

		public Stream<Option> children() {
			return depends.stream();
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), depends.hashCode());
			System.out.printf("\t%s [label=\"Any Of\", shape=\"invtriangle\"];\n", depends.hashCode());
			for (Option b: depends) {
				System.out.printf("\t%s->%s [label=\"Depends\"];\n", depends.hashCode(), b.hashCode());
			}
		}

        @Override
        public String toString() {
            return "DependsAny=" + depends;
        }
	}

	static class Missing implements Link {
		ModDependency.Only missing;

		public Missing(ModDependency.Only missing) {
			this.missing = missing;
		}

		public Stream<Option> children() {
			return Stream.empty();
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
			System.out.printf("\t%s [label=\"Missing: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.id());
		}

        @Override
        public String toString() {
            return "Missing=" + missing.id();
        }
	}

	static class MissingAny implements Link {
		ModDependency.Any missing;

		public MissingAny(ModDependency.Any missing) {
			this.missing = missing;
		}

		public Stream<Option> children() {
			return Stream.empty();
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Depends\"];\n", from.hashCode(), missing.hashCode());
			System.out.printf("\t%s [label=\"Missing any of: %s\", shape=\"ellipse\", color=red];\n", missing.hashCode(), missing.stream().map(ModDependency.Only::id).map(ModDependencyIdentifier::toString).collect(Collectors.joining(", ")));
		}

        @Override
        public String toString() {
            return "MissingAny=[" + missing.stream().map(ModDependency.Only::id).map(ModDependencyIdentifier::toString).collect(Collectors.joining(", ")) + "]";
        }
	}

	static class Duplicates implements Link {
		Collection<Option> options;
		String id;

		public Duplicates(Collection<Option> options, String id) {
			this.options = options;
			this.id = id;
		}

		public Stream<Option> children() {
			return options.stream();
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s[label=\"%s\"];\n", id, id);
			for(Option option: options){
				System.out.printf("\t%s->%s [label=\"Duplicates\", style=\"dashed\"];\n", option.hashCode(), id);
			}
		}

		@Override
		public String toString() {
			return "Duplicates=" + options;
		}
	}

	static class Manditory implements Link {
		Option option;

		public Manditory(Option option) {
			this.option = option;
		}

		public Stream<Option> children() {
			return Stream.of(option);
		}

		@Override
		public void dotGraphEdge(Option from) {
			// System.out.printf("\t%s->%s [label=\"Provides\", style=\"dashed\"];\n", from.hashCode(), option.hashCode());
		}

		@Override
		public String toString() {
			return "Manditory=" + option;
		}
	}

	static class Provided implements Link {
		Option provides;

		public Provided(Option provides) {
			this.provides = provides;
		}

		public Stream<Option> children() {
			return Stream.of(provides);
		}

		@Override
		public void dotGraphEdge(Option from) {
			System.out.printf("\t%s->%s [label=\"Provides\", style=\"dashed\"];\n", from.hashCode(), provides.hashCode());
		}

		@Override
		public String toString() {
			return "Provided=" + provides;
		}
	}

	private static LoadOption getTarget(LoadOption from) {
		if (from instanceof AliasedLoadOption) {
			LoadOption target = ((AliasedLoadOption) from).getTarget();
			if (target != null) {
				return target;
			}
		}
		return from;
	}

	static class RuleLink {
		final Rule rule;

		final List<OptionLink> from = new ArrayList<>();
		final List<OptionLink> to = new ArrayList<>();

		RuleLink(Rule rule) {
			this.rule = rule;
		}

		void addTo(OptionLink to) {
			this.to.add(to);
			to.from.add(this);
		}

		void addFrom(OptionLink from) {
			this.from.add(from);
			from.to.add(this);
		}

		@Override
		public String toString() {
			List<String> text = new ArrayList<>();
			rule.appendRuleDescription(t -> text.add(t.toString()));
			return "RuleLink{\n\trule: " + rule.getClass().getSimpleName() + "@" + String.join(" ", text) + "\n\tfrom: " + from + ",\n\tto: " + to + "\n}";
		}
	}

	static class OptionLink {
		final LoadOption option;

		final List<RuleLink> from = new ArrayList<>();
		final List<RuleLink> to = new ArrayList<>();

		OptionLink(LoadOption option) {
			this.option = option;
			if (LoadOption.isNegated(option)) {
				throw new IllegalArgumentException("Call 'OptionLinkBase.get' instead of this!!");
			}
		}

		@Override
		public String toString() {
			return "OptionLink{option: " + option.describe() + "}";
		}
	}

	private boolean reportKnownSolverError(List<RuleLink> rootRules) {
		if (rootRules.isEmpty()) {
			return false;
		}

		if (rootRules.size() == 1) {
			RuleLink rootRule = rootRules.get(0);

			if (rootRule.rule instanceof MandatoryModIdDefinition) {
				return reportSingleMandatoryError(rootRule);
			}

			return false;
		}

		return false;
	}

	/** Reports an error where there is only one root rule, of a {@link MandatoryModIdDefinition}. */
	private boolean reportSingleMandatoryError(RuleLink rootRule) {
		MandatoryModIdDefinition def = (MandatoryModIdDefinition) rootRule.rule;
		ModLoadOption mandatoryMod = def.option;

		if (rootRule.to.size() != 1) {//
			// This should always be the case, since a mandatory definition
			// always defines to a single source
			return false;
		}

		// TODO: Put this whole thing in a loop
		// and then check for transitive (and fully valid) dependency paths!

		OptionLink modLink = rootRule.to.get(0);
		List<OptionLink> modLinks = Collections.singletonList(modLink);
		Set<OptionLink> nextModLinks = new LinkedHashSet<>();
		List<List<OptionLink>> fullChain = new ArrayList<>();

		String groupOn = null;
		String modOn = null;
		ModDependencyIdentifier modIdOn = null;

		while (true) {
			groupOn = null;
			modOn = null;
			Boolean areAllInvalid = null;
			nextModLinks = new LinkedHashSet<>();

			for (OptionLink link : modLinks) {
				if (link.to.isEmpty()) {
					// Apparently nothing is stopping this mod from loading
					// (so there's a bug here somewhere)
					return false;
				}

				if (link.to.size() > 1) {
					// FOR NOW
					// just handle a single dependency / problem at a time
					return false;
				}

				RuleLink rule = link.to.get(0);
				if (rule.rule instanceof QuiltRuleDepOnly) {
					QuiltRuleDepOnly dep = (QuiltRuleDepOnly) rule.rule;

					ModDependencyIdentifier id = dep.publicDep.id();
					if (groupOn == null) {
						if (!id.mavenGroup().isEmpty()) {
							groupOn = id.mavenGroup();
						}
					} else if (!id.mavenGroup().isEmpty() && !groupOn.equals(id.mavenGroup())) {
						// A previous dep targets a different group of the same mod, so this is a branching condition
						return false;
					}

					if (modOn == null) {
						modOn = id.id();
					} else if (!modOn.equals(id.id())) {
						// A previous dep targets a different mod, so this is a branching condition
						return false;
					}

					modIdOn = id;

					if (dep.publicDep.unless() != null) {
						// TODO: handle 'unless' clauses!
						return false;
					}

					if (dep.getValidOptions().isEmpty()) {
						// Loop exit condition!
						if (areAllInvalid != null && areAllInvalid) {
							continue;
						} else if (nextModLinks.isEmpty()) {
							areAllInvalid = true;
							continue;
						} else {
							// Some deps are mismatched, others aren't
							// so this isn't necessarily a flat dep chain
							// (However it could be if the chain ends with a mandatory mod
							// like minecraft, which doesn't have anything else at the end of the chain
							return false;
						}
					}

					if (dep.getAllOptions().size() != rule.to.size()) {
						return false;
					}

					// Now check that they all match up
					for (LoadOption option : dep.getAllOptions()) {
						option = getTarget(option);
						boolean found = false;
						for (OptionLink link2 : rule.to) {
							if (option == link2.option) {
								found = true;
								nextModLinks.add(link2);
							}
						}
						if (!found) {
							return false;
						}
					}

				} else {
					// TODO: Handle other conditions!
					return false;
				}
			}

			fullChain.add(modLinks);

			if (areAllInvalid != null && areAllInvalid) {
				// Now we have validated that every mod in the previous list all depend on the same mod

				// Technically we should think about how to handle multiple *conflicting* version deps.
				// For example:
				// buildcraft requires abstract_base *
				// abstract_base 18 requires minecraft 1.18.x
				// abstract_base 19 requires minecraft 1.19.x

				// Technically we are just showing deps as "transitive" so
				// we just say that buildcraft requires minecraft 1.18.x or 1.19.x
				// so we need to make the required version list "bigger" rather than smaller

				// This breaks down if "abstract_base" requires an additional library though.
				// (Although we aren't handling that here)

				VersionRange fullRange = VersionRange.NONE;
				Set<ModLoadOption> allInvalidOptions = new HashSet<>();
				for (OptionLink link : fullChain.get(fullChain.size() - 1)) {
					// We validate all this earlier
					QuiltRuleDepOnly dep = (QuiltRuleDepOnly) link.to.get(0).rule;
					fullRange = VersionRange.ofRanges(Arrays.asList(fullRange, dep.publicDep.versionRange()));
					allInvalidOptions.addAll(dep.getWrongOptions());
				}

				for (OptionLink from : modLinks) {
					addError(new DependencyError(modIdOn, fullRange, (ModLoadOption) from.option, allInvalidOptions));
				}

				return true;
			}

			modLinks = new ArrayList<>(nextModLinks);
		}
	}

	private static void setIconFromMod(QuiltPluginManagerImpl manager, ModLoadOption mandatoryMod,
		QuiltDisplayedError error) {
		// TODO: Only upload a ModLoadOption's icon once!
		Map<String, byte[]> images = new HashMap<>();
		for (int size : new int[] { 16, 32 }) {
			String iconPath = mandatoryMod.metadata().icon(size);
			if (iconPath != null && ! images.containsKey(iconPath)) {
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

		/** Attempts to merge this error into the given error. This object itself shouldn't be modified.
		 * 
		 * @return True if the destination object was modified (and this was merged into it), false otherwise. */
		abstract boolean mergeInto(SolverError into);

		abstract void report(QuiltPluginManagerImpl manager);
	}

	static class UnhandledError extends SolverError {

		final Collection<Rule> rules;

		public UnhandledError(Collection<Rule> rules) {
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
		}
	}

	static class DependencyError extends SolverError {
		final ModDependencyIdentifier modOn;
		final VersionRange versionsOn;
		final Set<ModLoadOption> from = new LinkedHashSet<>();
		final Set<ModLoadOption> allInvalidOptions;

		DependencyError(ModDependencyIdentifier modOn, VersionRange versionsOn, ModLoadOption from, Set<ModLoadOption> allInvalidOptions) {
			this.modOn = modOn;
			this.versionsOn = versionsOn;
			this.from.add(from);
			this.allInvalidOptions = allInvalidOptions;
		}

		@Override
		boolean mergeInto(SolverError into) {
			if (into instanceof DependencyError) {
				DependencyError depDst = (DependencyError) into;
				if (!modOn.equals(depDst.modOn) || !versionsOn.equals(depDst.versionsOn)) {
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

			QuiltLoaderText first = VersionRangeDescriber.describe(rootModName, versionsOn, modOn.id(), transitive);

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

			Map<Path, ModLoadOption> realPaths = new LinkedHashMap<>();

			for (ModLoadOption mod : from) {
				Object[] modDescArgs = { mod.metadata().name(), manager.describePath(mod.from()) };
				error.appendDescription(QuiltLoaderText.translate("info.root_mod_loaded_from", modDescArgs));
				manager.getRealContainingFile(mod.from()).ifPresent(p -> realPaths.putIfAbsent(p, mod));
			}

			for (Map.Entry<Path, ModLoadOption> entry : realPaths.entrySet()) {
				error.addFileViewButton(entry.getKey()).icon(entry.getValue().modCompleteIcon());
			}

			String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
			if (issuesUrl != null) {
				error.addOpenLinkButton(QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl);
			}

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" requires");
			if (VersionRange.ANY.equals(versionsOn)) {
				report.append(" any version of ");
			} else {
				report.append(" version ").append(versionsOn).append(" of ");
			}
			report.append(modOn);// TODO
			report.append(", which is missing!");
			error.appendReportText(report.toString(), "");

			for (ModLoadOption mod : from) {
				error.appendReportText("- " + manager.describePath(mod.from()));
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

		BreakageError(ModDependency.Only dep, ModLoadOption from, Set<
			ModLoadOption> allBreakingOptions) {
			this.dep = dep;
			// this.versionsOn = versionsOn;
			this.from.add(from);
			this.allBreakingOptions = allBreakingOptions;
			// this.reason = reason;
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

			Map<Path, ModLoadOption> realPaths = new LinkedHashMap<>();

			for (ModLoadOption mod : from) {
				Object[] modDescArgs = { mod.metadata().name(), manager.describePath(mod.from()) };
				error.appendDescription(QuiltLoaderText.translate("info.root_mod_loaded_from", modDescArgs));
				manager.getRealContainingFile(mod.from()).ifPresent(p -> realPaths.putIfAbsent(p, mod));
			}

			for (ModLoadOption mod : allBreakingOptions) {
				Object[] modDescArgs = { mod.metadata().name(), manager.describePath(mod.from()) };
				error.appendDescription(QuiltLoaderText.translate("info.root_mod_loaded_from", modDescArgs));
				manager.getRealContainingFile(mod.from()).ifPresent(p -> realPaths.putIfAbsent(p, mod));
			}

			for (Map.Entry<Path, ModLoadOption> entry : realPaths.entrySet()) {
				error.addFileViewButton(entry.getKey()).icon(entry.getValue().modCompleteIcon());
			}

			String issuesUrl = mandatoryMod.metadata().contactInfo().get("issues");
			if (issuesUrl != null) {
				error.addOpenLinkButton(
					QuiltLoaderText.translate("button.mod_issue_tracker", mandatoryMod.metadata().name()), issuesUrl
				);
			}

			StringBuilder report = new StringBuilder(rootModName);
			report.append(" break");
			if (from.size() == 1) {
				report.append("s");
			}
			if (VersionRange.ANY.equals(dep.versionRange())) {
				report.append(" all versions of ");
			} else {
				report.append(" version ").append(dep.versionRange()).append(" of ");
			}
			report.append(dep.id());// TODO
			report.append(", which is present!");
			error.appendReportText(report.toString(), "");

			report = new StringBuilder();
			if (dep.unless() != null) {
				report.append("\n");
				if (dep.unless() instanceof ModDependency.Only) {
					ModDependency.Only unless = (ModDependency.Only) dep.unless();
					report.append("However, if");

					if (VersionRange.ANY.equals(unless.versionRange())) {
						report.append(" any version of ");
					} else {
						report.append(" a version ").append(unless.versionRange()).append(" of ");
					}

					report.append(unless.id()).append(" is present, ").append(rootModName).append(" do");
					if (from.size() == 1) {
						report.append("es");
					}
					report.append(" not break ").append(dep.id()).append(".");
				}
			}

			error.appendReportText(report.toString(), "");

			if (!dep.reason().isEmpty()) {
				error.appendReportText("Breaking mod's reason: " + dep.reason(), "");
			}

			error.appendReportText("Breaking mods: ");
			for (ModLoadOption mod : from) {
				error.appendReportText("- " + manager.describePath(mod.from()));
			}
		}
	}

	static class DuplicatesError extends SolverError {
		final String id;
		final Set<Option> duplicates = new LinkedHashSet<>();

		DuplicatesError(String id, Collection<Option> duplicates) {
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
			List<ModLoadOption> mandatories = new ArrayList<>();
			Set<String> commonIds = new LinkedHashSet<>();
			for (Option option : duplicates) {
				if (option.mandatory) {
					mandatories.add((ModLoadOption) option.option);
				} else if (option.option instanceof ProvidedModOption) {
					// TODO: Get the graph here
					// TEMP: assume its manditory
					mandatories.add(((ProvidedModOption) option.option));
				}
				commonIds.add(
					((ModLoadOption) option.option).id()
				);
			}

			if (mandatories.isEmpty()) {
				// So this means there's an OptionalModIdDefintion with only
				// DisabledModIdDefinitions as roots
				// that means this isn't a duplicate mandatory mods error!
				return;
			}

			ModLoadOption firstMandatory = mandatories.get(0);
			String bestName = null;
			for (ModLoadOption option : mandatories) {
				if (commonIds.contains(option.id())) {
					bestName = option.metadata().name();
					break;
				}
			}

			if (bestName == null) {
				bestName = "id'" + commonIds.iterator().next() + "'";
			}

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

			for (ModLoadOption option : mandatories) {
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

				if (option instanceof ProvidedModOption) {
					error.appendReportText("- Providing mod " + path);
				} else {
					error.appendReportText("- Mandatory mod " + path);
				}
			}

			error.appendDescription(QuiltLoaderText.translate("error.duplicate_mandatory.desc"));
		}
	}
}
