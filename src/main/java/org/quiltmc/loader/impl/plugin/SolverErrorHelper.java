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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.quiltmc.loader.impl.plugin.solvererror.BreaksAllError;
import org.quiltmc.loader.impl.plugin.solvererror.BreaksError;
import org.quiltmc.loader.impl.plugin.solvererror.DependsAnyError;
import org.quiltmc.loader.impl.plugin.solvererror.DependsError;
import org.quiltmc.loader.impl.plugin.solvererror.DuplicatesError;
import org.quiltmc.loader.impl.plugin.solvererror.SolverError;
import org.quiltmc.loader.impl.plugin.solvererror.UnhandledError;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Breaks;
import org.quiltmc.loader.impl.plugin.solvererror.graph.BreaksAll;
import org.quiltmc.loader.impl.plugin.solvererror.graph.DepLink;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Depends;
import org.quiltmc.loader.impl.plugin.solvererror.graph.DependsAny;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Duplicates;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Graph;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Link;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Mandatory;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Missing;
import org.quiltmc.loader.impl.plugin.solvererror.graph.MissingAny;
import org.quiltmc.loader.impl.plugin.solvererror.graph.Provided;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
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
	 * <p>
	 * If the system property {@value org.quiltmc.loader.impl.util.SystemProperties#PRINT_MOD_SOLVING_ERROR_DOT_GRAPH} is true,
	 * the dot graph for the errors is logged to the info logger.
	 */
	void reportErrors() {
//		if (SystemProperties.getBoolean(SystemProperties.PRINT_MOD_SOLVING_ERROR_DOT_GRAPH, false)) {
		graph.logGraph();
//		}
		SolverErrorReportContext context = new SolverErrorReportContext(this.manager, this.errors, this.graph);
		for (SolverError error : errors) {
			error.report(context);
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
			addError(new UnhandledError(rules));
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
				Optional<LoadOption> depends = Stream.concat(
								((QuiltRuleDepOnly) rule).getValidOptions().stream(),
								((QuiltRuleDepOnly) rule).getWrongOptions().stream())
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
			for (LoadOption option : graph.nodes()) {
				for (Link link : graph.edges(option)) {
					if (link instanceof Breaks) {
						Breaks breaks = ((Breaks) link);
						ModLoadOption from = (ModLoadOption) option;
						this.addError(new BreaksError(from, breaks.getRule()));
						added = true;
					} else if (link instanceof BreaksAll) {
						BreaksAll breaksAll = ((BreaksAll) link);
						ModLoadOption from = (ModLoadOption) option;
						if (breaksAll.breaks().size() == 1) {
							this.addError(new BreaksError(from, breaksAll.getRule().options[0]));
						} else {
							this.addError(new BreaksAllError(from, breaksAll.getRule()));
						}
						added = true;
					} else if (link instanceof DepLink) {
						DepLink<?> dep = ((DepLink<?>) link);
						ModLoadOption from = (ModLoadOption) option;

						Set<QuiltRuleDepOnly> rules = flattenUnless(dep.getRule());
						if (rules.size() > 1) {
							this.addError(new DependsAnyError(from, rules));
						} else {
							this.addError(new DependsError(from, rules.iterator().next()));
						}

						added = true;
					}
				}

				for (Link link : graph.edgesTo(option)) {
					if (link instanceof Duplicates) {
						Duplicates duplicates = (Duplicates) link;
						this.addError(new DuplicatesError(duplicates.id(), duplicates.options()));
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
	 *
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
}
