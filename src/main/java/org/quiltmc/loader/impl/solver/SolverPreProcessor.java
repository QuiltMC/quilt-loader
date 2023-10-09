/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.NegatedLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.solver.RuleComputeResult.DeclaredConstants;
import org.quiltmc.loader.impl.solver.RuleSet.ProcessedRuleSet;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;

/** Pre-processes a {@link RuleSet} to reduce the problem that we pass to sat4j. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class SolverPreProcessor {

	// API

	/** @return The processed {@link RuleSet} if we were successful.
	 * @throws ContradictionException if there is a contradiction in the input rules. */
	static ProcessedRuleSet preProcess(RuleSet rules) throws ContradictionException {
		return new SolverPreProcessor(rules).process();
	}

	// Internals

	private final Map<LoadOption, Boolean> constants;
	private final Map<LoadOption, LoadOption> aliases;
	private final Map<LoadOption, Integer> options;

	/** All non-constant definitions. */
	private final Set<RuleDefinition> activeRules = new LinkedHashSet<>();

	/** A map of every {@link LoadOption} to {@link RuleDefinition}s that reference it. */
	private final Map<LoadOption, Set<RuleDefinition>> option2rules = new HashMap<>();

	/** Every current {@link RuleDefinition} which is currently constant, and can push that down to {@link LoadOption}s.
	 * (Basically the cached value of {@link RuleDefinition#isConstant()} */
	private final Set<RuleDefinition> rulesToVisit = new HashSet<>();

	/** Every {@link LoadOption} that isn't affected by any rules. This generally means we set this to a constant based
	 * on it's weight. */
	private final Set<LoadOption> optionsWithoutRules = new HashSet<>();

	/** Every set of options which are treated identically by a set of rules. */
	private final Map<Set<LoadOption>, Set<RuleDefinition>> optionSet2rules = new HashMap<>();

	private SolverPreProcessor(RuleSet rules) {
		this.constants = new HashMap<>(rules.constants);
		this.aliases = new HashMap<>(rules.aliases);
		this.options = new HashMap<>(rules.options);
		this.optionsWithoutRules.addAll(options.keySet());

		rules.forEachRule(this::addRule);
	}

	private SolverPreProcessor(SolverPreProcessor parent, Map<LoadOption, Integer> optionSubMap, Set<
		RuleDefinition> ruleSubSet) {
		this.constants = parent.constants;
		this.aliases = parent.aliases;
		this.options = optionSubMap;
		this.optionsWithoutRules.addAll(options.keySet());
		ruleSubSet.forEach(this::addRule);
	}

	private void addRule(RuleDefinition rule) {
		activeRules.add(rule);
		if (rule.isConstant()) {
			rulesToVisit.add(rule);
		}

		Set<LoadOption> optionSet = new HashSet<>();
		for (LoadOption option : rule.options) {
			optionSet.add(option);
			if (option instanceof NegatedLoadOption) {
				option = ((NegatedLoadOption) option).not;
			}
			option = aliases.getOrDefault(option, option);
			option2rules.computeIfAbsent(option, o -> new HashSet<>()).add(rule);
			optionsWithoutRules.remove(option);
		}
		optionSet2rules.computeIfAbsent(optionSet, set -> new HashSet<>()).add(rule);
	}

	private void removeRule(RuleDefinition rule) {
		activeRules.remove(rule);
		rulesToVisit.remove(rule);

		Set<LoadOption> optionSet = new HashSet<>();
		for (LoadOption option : rule.options) {
			optionSet.add(option);
			if (option instanceof NegatedLoadOption) {
				option = ((NegatedLoadOption) option).not;
			}
			option = aliases.getOrDefault(option, option);
			Set<RuleDefinition> rules = option2rules.get(option);
			if (rules != null) {
				rules.remove(rule);
				if (rules.isEmpty()) {
					optionsWithoutRules.add(option);
				}
			}
		}

		Set<RuleDefinition> set = optionSet2rules.get(optionSet);
		set.remove(rule);
		if (set.isEmpty()) {
			optionSet2rules.remove(optionSet);
		}
	}

	private Boolean getConstantValue(LoadOption option) {
		boolean negate = false;
		if (option instanceof NegatedLoadOption) {
			option = ((NegatedLoadOption) option).not;
			negate = true;
		}

		option = aliases.getOrDefault(option, option);
		Boolean value = constants.get(option);
		if (value == null) {
			return null;
		}

		return negate ? !value : value;
	}

	private ProcessedRuleSet process() throws ContradictionException {
		final Function<LoadOption, Boolean> funcGetConstant = this::getConstantValue;

		boolean changed;
		do {
			changed = false;

			while (!rulesToVisit.isEmpty()) {
				RuleDefinition rule = rulesToVisit.iterator().next();
				RuleComputeResult result = rule.computeConstants(funcGetConstant);
				if (result == RuleComputeResult.IDENTICAL) {
					rulesToVisit.remove(rule);
					continue;
				}

				if (result == RuleComputeResult.CONTRADICTION) {
					throw new ContradictionException();
				}

				if (result == RuleComputeResult.TRIVIALLY_REMOVED) {
					removeRule(rule);
					changed = true;
					continue;
				}

				final RuleDefinition newRule;
				if (result instanceof RuleComputeResult.DeclaredConstants) {
					RuleComputeResult.DeclaredConstants declared = (DeclaredConstants) result;
					if (result instanceof RuleComputeResult.Removed) {
						newRule = null;
					} else if (result instanceof RuleComputeResult.Changed) {
						newRule = ((RuleComputeResult.Changed) result).newDefintion;
					} else {
						throw new IllegalStateException(
							"Unknown subtype of RuleComputeResult " + result.getClass() + " " + result
						);
					}

					for (Map.Entry<LoadOption, Boolean> entry : declared.newConstants.entrySet()) {
						LoadOption option = entry.getKey();
						boolean newValue = entry.getValue();

						if (option instanceof NegatedLoadOption) {
							option = ((NegatedLoadOption) option).not;
							newValue = !newValue;
						}
						option = aliases.getOrDefault(option, option);
						Boolean currentValue = constants.get(option);
						if (currentValue != null) {
							if (currentValue == newValue) {
								// Nothing changed
								continue;
							} else {
								throw new ContradictionException();
							}
						} else {
							constants.put(option, newValue);
							options.remove(option);
						}

						// Check all of the rules that affected it to see if they need to be propagated as well.
						// ...Although instead of *actually* checking, we just append it to the list to be checked later
						rulesToVisit.addAll(option2rules.remove(option));
					}

				} else {
					throw new IllegalStateException(
						"Unknown subtype of RuleComputeResult " + result.getClass() + " " + result
					);
				}

				removeRule(rule);
				if (newRule != null) {
					addRule(newRule);
				}
				changed = true;
			}

			for (LoadOption option : optionsWithoutRules) {
				// Double-check that we don't already have a constant value
				Boolean currentValue = constants.get(option);
				if (currentValue == null) {
					int weight = options.remove(option);
					// Load options if their weight is negative
					// Don't load options if their weight is positive
					// If the weight is zero then it's less clear
					// Since we have no idea if it's a good idea to load the option or not
					// For now, just reject them since we assume every option actually does something.
					if (weight == 0) {
						Log.info(Sat4jWrapper.CATEGORY, option + " is undecided, and has a weight of 0 ?");
					}
					constants.put(option, weight < 0);
				} else {
					options.remove(option);
				}
				changed = true;
			}
			optionsWithoutRules.clear();

			if (activeRules.size() == 1) {
				// 1 rule left, that means we can choose a value for every constant
				RuleDefinition rule = activeRules.iterator().next();
				chooseBasedOnOnly(rule, true);

				changed = true;
			}

			for (Map.Entry<Set<LoadOption>, Set<RuleDefinition>> entry : new ArrayList<>(optionSet2rules.entrySet())) {
				Set<LoadOption> optionSet = entry.getKey();
				Set<RuleDefinition> rules = new HashSet<>(entry.getValue());
				if (rules.size() == 1) {
					continue;
				}
				assert !rules.isEmpty();
				// All rules boil down to "at least" and "at most"
				// So we can always simplify down to just "between"
				int min = 0;
				int max = optionSet.size();
				RuleDefinition currentMin = null;
				RuleDefinition currentMax = null;
				for (RuleDefinition def : rules) {
					int min2 = def.minimum();
					if (min2 > min) {
						min = min2;
						currentMin = def;
					}
					int max2 = def.maximum();
					if (max2 < max) {
						max = max2;
						currentMax = def;
					}
				}
				if (max < min) {
					throw new ContradictionException();
				}
				Set<RuleDefinition> remaining = new HashSet<>();
				remaining.add(currentMin);
				remaining.add(currentMax);
				remaining.remove(null);
				final Set<RuleDefinition> removing;
				if (remaining.size() == 1) {
					removing = new HashSet<>();
					removing.addAll(rules);
					removing.removeAll(remaining);
				} else {
					removing = rules;
					final Rule rule = remaining.iterator().next().rule;
					final LoadOption[] array = optionSet.toArray(new LoadOption[0]);
					if (max < optionSet.size()) {
						if (min == max) {
							addRule(new RuleDefinition.Exactly(rule, max, array));
						} else if (min > 0) {
							addRule(new RuleDefinition.Between(rule, min, max, array));
						} else {
							addRule(new RuleDefinition.AtMost(rule, max, array));
						}
					} else if (min == 1) {
						addRule(new RuleDefinition.AtLeastOneOf(rule, array));
					} else if (min > 0) {
						addRule(new RuleDefinition.AtLeast(rule, min, array));
					} else {
						// None of the rules affect the options
					}
				}
				removing.forEach(this::removeRule);
				changed = true;
			}

			// Separate remaining rules into separate problems
			// We do this before any expensive "for each rule, check each rule" steps
			List<SolverPreProcessor> subProblems = new ArrayList<>();
			{
				Set<LoadOption> remainingOptions = new HashSet<>();
				Set<RuleDefinition> remainingRules = new HashSet<>();
				remainingOptions.addAll(options.keySet());
				remainingRules.addAll(activeRules);
				while (!remainingOptions.isEmpty()) {
					LoadOption next = remainingOptions.iterator().next();
					remainingOptions.remove(next);
					Set<LoadOption> openSet = new HashSet<>();
					openSet.add(next);
					Map<LoadOption, Integer> closedSet = new HashMap<>();
					Set<RuleDefinition> ruleSubSet = new HashSet<>();
					while (!openSet.isEmpty()) {
						LoadOption sub = openSet.iterator().next();
						openSet.remove(sub);
						closedSet.put(sub, options.get(sub));
						for (RuleDefinition def : option2rules.get(sub)) {
							remainingRules.remove(def);
							ruleSubSet.add(def);
							for (LoadOption option : def.options) {
								if (LoadOption.isNegated(option)) {
									option = option.negate();
								}
								if (remainingOptions.remove(option)) {
									openSet.add(option);
								}
							}
						}
					}
					subProblems.add(new SolverPreProcessor(this, closedSet, ruleSubSet));
				}
			}

			if (subProblems.isEmpty()) {
				// No remaining options or rules
			} else if (subProblems.size() == 1) {
				// We weren't able to separate the problem any further, so there's nothing else we can do
				changed |= detectRedundentSubRules();
				changed |= searchForSelfSufficientRules();
			} else {

				Map<LoadOption, Integer> remainingOptions = new HashMap<>();
				Set<RuleDefinition> remainingRules = new HashSet<>();

				for (SolverPreProcessor processor : subProblems) {
					processor.detectRedundentSubRules();
					ProcessedRuleSet processedSet = processor.process();
					if (processedSet == null) {
						throw new ContradictionException();
					}
					remainingOptions.putAll(processedSet.options);
					remainingRules.addAll(processedSet.rules);

					if (processedSet.isFullySolved()) {
						continue;
					}

					Log.info(Sat4jWrapper.CATEGORY, "");
					Log.info(Sat4jWrapper.CATEGORY, "Unsolved Sub Problem: ");

					// Log rule & options
					// but as letters and array indices
					Map<LoadOption, String> options = new HashMap<>();
					char letter = 'A';
					for (LoadOption option : processedSet.options.keySet().stream().sorted(Comparator.comparing(LoadOption::toString)).toArray(LoadOption[]::new)) {
						Log.info(Sat4jWrapper.CATEGORY, letter + ": " + option + " weight " + processedSet.options.get(option));
						options.put(option, "+" + letter);
						options.put(option.negate(), "-" + letter);

						if (letter == 'Z') {
							letter = 'a';
						} else if (letter == 'z') {
							letter = '0';
						} else {
							letter++;
						}
					}

					List<String> rules = new ArrayList<>();
					for (RuleDefinition def : processedSet.rules) {
						StringBuilder sb = new StringBuilder();

						switch (def.type()) {
							case AT_LEAST: {
								sb.append("AtLeast    " + pad(def.minimum()) + "    ");
								break;
							}
							case AT_MOST: {
								sb.append("At Most    " + pad(def.maximum()) + "    ");
								break;
							}
							case EXACTLY: {
								sb.append("Exactly    " + pad(def.maximum()) + "    ");
								break;
							}
							case BETWEEN: {
								sb.append("Between " + pad(def.minimum()) + " and " + pad(def.maximum()));
								break;
							}
						}
						sb.append(" of ");
						sb.append(pad(def.options.length));
						sb.append("{ ");
						boolean first = true;
						for (LoadOption option : Stream.of(def.options).sorted(Comparator.comparing(LoadOption::toString)).toArray(LoadOption[]::new)) {
							if (first) {
								first = false;
							} else {
								sb.append(", ");
							}
							sb.append(options.get(option));
						}
						sb.append(" }");

						rules.add(sb.toString());
					}
					rules.sort(null);
					for (String rule : rules) {
						Log.info(Sat4jWrapper.CATEGORY, rule);
					}
				}

				return new ProcessedRuleSet(constants, aliases, remainingOptions, new ArrayList<>(remainingRules));
			}
		} while (changed);

		return new ProcessedRuleSet(constants, aliases, options, new ArrayList<>(activeRules));
	}

	/** Picks a value for every {@link LoadOption} in this rule. This assumes that no other option is affected by this
	 * choice. */
	private void chooseBasedOnOnly(RuleDefinition rule, boolean putConstants) {
		int min = rule.minimum();
		int max = rule.maximum();
		// Desired options are ones with a negative weight, ordered by least wanted to most wanted
		List<LoadOption> desiredOptions = new ArrayList<>();
		// Unwanted options are ones with a positive weight, ordered by least wanted to most wanted
		List<LoadOption> unwantedOptions = new ArrayList<>();
		// Indifferent options are ones with a weight of 0.
		List<LoadOption> indifferentOptions = new ArrayList<>();

		List<LoadOption> allOptions = new ArrayList<>();
		Collections.addAll(allOptions, rule.options);
		ToIntFunction<LoadOption> getWeight = option -> {
			if (LoadOption.isNegated(option)) {
				return -options.get(option.negate());
			}
			return options.get(option);
		};
		allOptions.sort(Comparator.comparingInt(getWeight).reversed());

		LoadOption last = null;
		int lastWeight = 0;
		for (LoadOption option : allOptions) {

			int weight = getWeight.applyAsInt(option);

			if (last != null && weight == lastWeight) {
				Log.warn(Sat4jWrapper.CATEGORY, "Two options have identical weight when choosing between them!");
				Log.warn(Sat4jWrapper.CATEGORY, last.toString());
				Log.warn(Sat4jWrapper.CATEGORY, option.toString());
			}

			if (weight > 0) {
				unwantedOptions.add(option);
			} else if (weight < 0) {
				desiredOptions.add(option);
			} else {
				indifferentOptions.add(option);
			}

			last = option;
			lastWeight = weight;
		}

		Set<LoadOption> takenOptions = new HashSet<>();

		while (takenOptions.size() < min) {
			if (!desiredOptions.isEmpty()) {
				takenOptions.add(desiredOptions.remove(desiredOptions.size() - 1));
				continue;
			}
			if (!indifferentOptions.isEmpty()) {
				takenOptions.add(indifferentOptions.remove(indifferentOptions.size() - 1));
			}
			takenOptions.add(unwantedOptions.remove(unwantedOptions.size() - 1));
		}

		while (takenOptions.size() < max) {
			if (desiredOptions.isEmpty()) {
				break;
			}
			takenOptions.add(desiredOptions.remove(desiredOptions.size() - 1));
		}

		for (LoadOption option : allOptions) {
			boolean value = takenOptions.remove(option);
			if (LoadOption.isNegated(option)) {
				option = option.negate();
				value = !value;
			}
			if (putConstants) {
				constants.put(option, value);
				options.remove(option);
			} else if (value) {
				addRule(new RuleDefinition.AtLeast(rule.rule, 1, new LoadOption[] { option }));
			} else {
				addRule(new RuleDefinition.AtMost(rule.rule, 0, new LoadOption[] { option }));
			}
		}

		removeRule(rule);
		assert takenOptions.isEmpty();
	}

	private String pad(int value) {
		if (value < 10) {
			return " " + value;
		} else {
			return "" + value;
		}
	}

	/** Checks every rule to see if it's options don't actually affect the choice of any other options.
	 * 
	 * @return if anything changed. */
	private boolean searchForSelfSufficientRules() throws ContradictionException {
		boolean changed = false;
		for (RuleDefinition rule : new ArrayList<>(activeRules) ){
			if (checkForSelfSufficientRule(rule)) {
				changed = true;
			}
		}
		return changed;
	}

	private boolean checkForSelfSufficientRule(RuleDefinition rule) {
		Map<RuleDefinition, Set<LoadOption>> replacedRules = new HashMap<>();
		LoadOption with = rule.options[0];
		for (LoadOption option : rule.options) {
			LoadOption original = option;
			if (LoadOption.isNegated(option)) {
				option = option.negate();
			}
			boolean foundMatch = false;
			for (RuleDefinition rule2 : option2rules.get(option)) {
				if (rule2 == rule) {
					continue;
				}
				RuleDefinition simplified = rule2.blindlyReplace(original, with);
				simplified = simplified.blindlyReplace(original.negate(), with.negate());
				replacedRules.computeIfAbsent(simplified, r -> new HashSet<>()).add(original);
			}
		}

		if (replacedRules.isEmpty()) {
			// None of the options are even mentioned by any other rules
			// That means we don't need to bother checking anything else
			chooseBasedOnOnly(rule, true);
			return true;
		}

		Set<LoadOption> expected = new HashSet<>();
		Collections.addAll(expected, rule.options);
		for (Set<LoadOption> set : replacedRules.values()) {
			if (!expected.equals(set)) {
				return false;
			}
		}

		// If we've got here then we have a rule of which it's options only interact with other options in the same way
		Log.info(Sat4jWrapper.CATEGORY, "Found self-sufficient candidate " + rule);
		chooseBasedOnOnly(rule, false);
		return true;
	}

	/** Checks every rule to see if it is a "redundant sub-set" of another rule. This mostly handles common
	 * dependencies, but where some of the possible versions aren't valid for every dependency.
	 * 
	 * @return if anything changed. */
	private boolean detectRedundentSubRules() throws ContradictionException {

		boolean anythingChanged = false;
		boolean changedThisLoop = false;

		// For simplicities sake we just restart the loop anytime we handle redundant elements
		outer_loop: do {
			changedThisLoop = false;

			for (RuleDefinition rule1 : activeRules) {

				for (LoadOption option : rule1.options) {
					if (LoadOption.isNegated(option)) {
						option = option.negate();
					}

					for (RuleDefinition rule2 : option2rules.get(option)) {

						if (rule2.options.length >= rule1.options.length) {
							// Handle rule2 > rule1 in another pass

							// For rule1.length == rule2.length:
							// Since the rules affect exactly the same rules
							// this will have already been handled by the
							// "optionSet2rules" field and it's handling
							continue;
						}

						LoadOption[] excluded = computeExcluded(rule1, rule2);

						if (excluded == null) {
							continue;
						}

						changedThisLoop = checkRulesForRedundency(rule1, rule2, excluded);

						if (changedThisLoop) {
							anythingChanged = true;
							continue outer_loop;
						}
					}
				}
			}

		} while (changedThisLoop);

		return anythingChanged;
	}

	private static LoadOption[] computeExcluded(RuleDefinition rule1, RuleDefinition rule2) {
		int excludedIndex = 0;
		LoadOption[] excluded = new LoadOption[rule1.options.length - rule2.options.length];

		int idx2 = 0;
		for (int idx1 = 0; idx1 < rule1.options.length; idx1++) {
			LoadOption op1 = rule1.options[idx1];
			LoadOption op2 = idx2 >= rule2.options.length ? null : rule2.options[idx2];
			if (op1.equals(op2)) {
				idx2++;
				continue;
			}
			if (excludedIndex == excluded.length) {
				// Too many
				return null;
			}
			excluded[excludedIndex++] = op1;
		}

		if (idx2 != rule2.options.length || excludedIndex != excluded.length) {
			excluded = null;
		}
		return excluded;
	}

	/** Checks to see if we can simplify something based on two rules, where the smaller affects a strict subset of the
	 * larger. */
	private boolean checkRulesForRedundency(RuleDefinition larger, RuleDefinition smaller, LoadOption[] excluded) throws ContradictionException {

		// Definitions:
		// "Included" is the set of options that are in both sets. It's equal to the options in smaller
		// "Excluded" is the set of options that are in the larger set ONLY.

		// A previous version of this method used a loop and "setAsConstant" to set constant values
		// This causes problems since it doesn't properly propagate the constants to the other rules
		// So instead we add a rule of "At Most 0" in order for the initial constant propagation to handle it.

		// We have 4 types of rules, and larger and smaller could be any of either
		// So the clearest way of handling this is to just enumerate them (all 16 possibilities)
		switch (larger.type()) {
			case AT_LEAST: {
				// larger is AT LEAST minL
				int minL = larger.minimum();
				switch (smaller.type()) {
					case AT_LEAST: {
						// larger is AT LEAST minL
						// smaller is AT LEAST minS
						int minS = smaller.minimum();
						if (minL <= minS) {
							// Larger is fully redundant
							removeRule(larger);
							return true;
						} else {
							// We can't assume anything here
							return false;
						}
					}
					case AT_MOST: {
						// larger is AT LEAST minL
						// smaller is AT MOST maxS
						int maxS = smaller.maximum();
						if (maxS < smaller.options.length) {
							int maxL = larger.options.length - smaller.options.length + maxS;
							removeRule(larger);
							if (maxL == minL) {
								addRule(new RuleDefinition.Exactly(larger.rule, minL, larger.options));
							} else {
								addRule(new RuleDefinition.Between(larger.rule, minL, maxL, larger.options));
							}
							return true;
						}
						return false;
					}
					case EXACTLY: {
						// larger is AT LEAST minL
						// smaller is EXACTLY maxS
						int maxS = smaller.maximum();
						int minExcluded = minL - maxS;
						if (minExcluded <= 0) {
							removeRule(larger);
						} else {
							removeRule(larger);
							addRule(new RuleDefinition.AtLeast(larger.rule, minExcluded, excluded));
						}
						return true;
					}
					case BETWEEN: {
						// larger is AT LEAST minL
						// smaller is BETWEEN minS and maxS
						int minS = smaller.minimum();
						int maxS = smaller.maximum();
						// use the same logic as AT_LEAST since we can't make use of the upper bound
						if (minL <= minS) {
							// Larger is fully redundant
							removeRule(larger);
							return true;
						} else {
							// We can't assume anything here
							return false;
						}
					}
					default: {
						throw new IllegalStateException("Unknown/new rule type " + smaller.type());
					}
				}
			}
			case AT_MOST: {
				// larger is AT MOST maxL
				int maxL = larger.maximum();
				switch (smaller.type()) {
					case AT_LEAST: {
						// larger is AT MOST maxL
						// smaller is AT LEAST minS
						int minS = smaller.minimum();
						if (maxL < minS) {
							// Contradiction
							throw new ContradictionException();
						} else if (maxL == minS) {
							// Excluded must all be FALSE
							// Included is replaced with EXACTLY maxL
							removeRule(smaller);
							removeRule(larger);
							addRule(new RuleDefinition.Exactly(smaller.rule, minS, smaller.options));
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							return true;
						} else {
							return false;
						}
					}
					case AT_MOST: {
						// larger is AT MOST maxL
						// smaller is AT MOST maxS
						int maxS = smaller.maximum();

						if (maxL == maxS) {
							removeRule(smaller);
							return true;
						} else {
							return false;
						}
					}
					case EXACTLY: {
						// larger is AT MOST maxL
						// smaller is EXACTLY minS
						int minS = smaller.minimum();

						if (minS > maxL) {
							throw new ContradictionException();
						} else {
							removeRule(larger);
							addRule(new RuleDefinition.AtMost(larger.rule, maxL - minS, excluded));
							return true;
						}
					}
					case BETWEEN: {
						// larger is AT MOST maxL
						// smaller is BETWEEN minS and maxS
						int minS = smaller.minimum();
						int maxS = smaller.maximum();
						// minS < maxS is validated by Between#type()

						if (maxL < minS) {
							throw new ContradictionException();
						}

						if (maxL == minS) {
							// This works the same as AT_MOST...AT_LEAST:
							// Excluded must all be FALSE
							// Included is replaced with EXACTLY maxL
							removeRule(smaller);
							removeRule(larger);
							addRule(new RuleDefinition.Exactly(smaller.rule, minS, smaller.options));
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							return true;
						}

						if (maxL > maxS) {
							return false;
						}

						if (maxL < maxS) {
							// We need to change maxS to equal maxL
							removeRule(smaller);
							addRule(new RuleDefinition.Between(smaller.rule, minS, maxL, smaller.options));
							return true;
						} else {
							return false;
						}
					}
					default: {
						throw new IllegalStateException("Unknown/new rule type " + smaller.type());
					}
				}
			}
			case EXACTLY: {
				// larger is EXACTLY exactL
				int exactL = larger.minimum();
				switch (smaller.type()) {
					case AT_LEAST: {
						// larger is EXACTLY minL
						// smaller is AT LEAST minS
						int minS = smaller.minimum();

						if (exactL < minS) {
							throw new ContradictionException();
						}

						if (exactL == minS) {
							// Excluded is all FALSE
							// replace both with EXACTLY exactL of Included
							removeRule(larger);
							removeRule(smaller);
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							addRule(new RuleDefinition.Exactly(larger.rule, exactL, smaller.options));
							return true;
						} else {
							// exactL > minS
							return false;
						}
					}
					case AT_MOST: {
						// larger is EXACTLY exactL
						// smaller is AT MOST maxS
						int maxS = smaller.maximum();
						if (maxS > exactL) {
							// The only thing we can do is propagate the smaller number down
							removeRule(smaller);
							addRule(new RuleDefinition.AtMost(smaller.rule, exactL, smaller.options));
							return true;
						}
						return false;
					}
					case EXACTLY: {
						// larger is EXACTLY exactL
						// smaller is EXACTLY minS
						int minS = smaller.minimum();
						if (exactL < minS) {
							throw new ContradictionException();
						}

						if (exactL == minS) {
							// Excluded is all false
							removeRule(larger);
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							return true;
						}

						// exactL > minS
						removeRule(larger);
						addRule(new RuleDefinition.Exactly(larger.rule, exactL - minS, excluded));
						return true;
					}
					case BETWEEN: {
						// larger is EXACTLY exactL
						// smaller is BETWEEN minS and maxS
						int minS = smaller.minimum();
						int maxS = smaller.maximum();

						if (exactL < minS) {
							throw new ContradictionException();
						}

						if (exactL == minS) {
							removeRule(larger);
							removeRule(smaller);
							addRule(new RuleDefinition.Exactly(smaller.rule, exactL, smaller.options));
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							return true;
						}

						if (exactL >= maxS) {
							// There's nothing we can do here
							return false;
						}

						// Redefine the smaller rule to have an upper bound equal to exactL
						removeRule(smaller);
						addRule(new RuleDefinition.Between(smaller.rule, minS, exactL, smaller.options));
						return true;
					}
					default: {
						throw new IllegalStateException("Unknown/new rule type " + smaller.type());
					}
				}
			}
			case BETWEEN: {
				// larger is BETWEEN minL and maxL
				int minL = larger.minimum();
				int maxL = larger.maximum();
				switch (smaller.type()) {
					case AT_LEAST: {
						// larger is BETWEEN minL and maxL
						// smaller is AT LEAST minS
						int minS = smaller.minimum();

						if (minS > maxL) {
							throw new ContradictionException();
						}

						if (minS <= minL) {
							// The only thing we can do here is push down the maximum
							if (maxL < smaller.options.length) {
								removeRule(smaller);
								addRule(new RuleDefinition.Between(smaller.rule, minS, maxL, smaller.options));
								return true;
							} else {
								return false;
							}
						}

						if (minS < maxL) {
							// Pull up the minimum
							removeRule(larger);
							addRule(new RuleDefinition.Between(larger.rule, minS, maxL, larger.options));

							// And maybe push down the maximum
							if (maxL < smaller.options.length) {
								removeRule(smaller);
								addRule(new RuleDefinition.Between(smaller.rule, minS, maxL, smaller.options));
							}
							return true;
						}

						// minS == maxL
						// Redefine both as EXACTLY
						// However this means we can apply the same logic as EXACTLY...EXACTLY
						// Which means the smaller rule is kept as an EXACTLY
						removeRule(smaller);
						addRule(new RuleDefinition.Exactly(smaller.rule, minS, smaller.options));
						// and the larger rule is removed, with all Excluded set to false
						removeRule(larger);
						addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
						return true;
					}
					case AT_MOST: {
						// larger is BETWEEN minL and maxL
						// smaller is AT MOST maxS
						int maxS = smaller.maximum();

						// I don't think we can work out anything here
						// Except the obvious case if maxS > maxL
						if (maxS > maxL) {
							removeRule(smaller);
							addRule(new RuleDefinition.AtMost(smaller.rule, maxL, smaller.options));
							return true;
						}

						return false;
					}
					case EXACTLY: {
						// larger is BETWEEN minL and maxL
						// smaller is EXACTLY exactS
						int exactS = smaller.minimum();

						if (exactS < minL) {
							removeRule(larger);
							addRule(new RuleDefinition.Between(larger.rule, minL - exactS, maxL - exactS, excluded));
							return true;
						} else if (exactS <= maxL) {
							removeRule(larger);
							addRule(new RuleDefinition.AtMost(larger.rule, maxL - exactS, excluded));
							return true;
						}

						// exactS > maxL
						throw new ContradictionException();
					}
					case BETWEEN: {
						// larger is BETWEEN minL and maxL
						// smaller is BETWEEN minS and maxS
						int minS = smaller.minimum();
						int maxS = smaller.maximum();

						if (minS > maxL) {
							throw new ContradictionException();
						}

						if (minS == maxL) {
							// Smaller is now Exactly minS
							// Excluded is now At Most 0
							removeRule(smaller);
							addRule(new RuleDefinition.Exactly(smaller.rule, minS, smaller.options));
							removeRule(larger);
							addRule(new RuleDefinition.AtMost(larger.rule, 0, excluded));
							return true;
						}

						boolean redefineLarger = false;
						boolean redefineSmaller = false;

						if (minL < minS) {
							redefineLarger = true;
							minL = minS;
						}

						if (maxL < maxS) {
							redefineSmaller = true;
							maxS = maxL;
						}

						if (redefineLarger) {
							removeRule(larger);
							addRule(new RuleDefinition.Between(larger.rule, minL, maxL, larger.options));
						}

						if (redefineSmaller) {
							removeRule(smaller);
							addRule(new RuleDefinition.Between(smaller.rule, minS, maxS, smaller.options));
						}

						return redefineLarger | redefineSmaller;
					}
					default: {
						throw new IllegalStateException("Unknown/new rule type " + smaller.type());
					}
				}
			}
			default: {
				throw new IllegalStateException("Unknown/new rule type " + larger.type());
			}
		}
	}
}
