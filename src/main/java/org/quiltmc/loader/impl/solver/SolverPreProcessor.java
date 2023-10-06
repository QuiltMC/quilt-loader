package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.solver.RuleComputeResult.DeclaredConstants;
import org.quiltmc.loader.impl.solver.RuleSet.ProcessedRuleSet;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;

/** Pre-processes a {@link RuleSet} to reduce the problem that we pass to sat4j. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class SolverPreProcessor {

	// API

	/** @return null if there was a contradiction in the source rules, or a processed {@link RuleSet} if we were
	 *         successful. */
	@Nullable
	static ProcessedRuleSet preProcess(RuleSet rules) {
		return new SolverPreProcessor(rules).process();
	}

	// Internals

	private final Map<LoadOption, Boolean> constants = new HashMap<>();
	private final Map<LoadOption, LoadOption> aliases = new HashMap<>();
	private final Map<LoadOption, Integer> options = new HashMap<>();

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

	private SolverPreProcessor(RuleSet rules) {
		this.constants.putAll(rules.constants);
		this.aliases.putAll(rules.aliases);
		this.options.putAll(rules.options);
		this.optionsWithoutRules.addAll(options.keySet());

		rules.forEachRule(this::addRule);
	}

	private void addRule(RuleDefinition rule) {
		activeRules.add(rule);
		if (rule.isConstant()) {
			rulesToVisit.add(rule);
		}

		for (LoadOption option : rule.options) {
			if (option instanceof NegatedLoadOption) {
				option = ((NegatedLoadOption) option).not;
			}
			option = aliases.getOrDefault(option, option);
			option2rules.computeIfAbsent(option, o -> new HashSet<>()).add(rule);
			optionsWithoutRules.remove(option);
		}
	}

	private void removeRule(RuleDefinition rule) {
		activeRules.remove(rule);
		rulesToVisit.remove(rule);

		for (LoadOption option : rule.options) {
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

	@Nullable
	private ProcessedRuleSet process() {
		final Function<LoadOption, Boolean> funcGetConstant = this::getConstantValue;

		// Steps:
		// 1: propogate constants
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
					return null;
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
								// A contradiction
								return null;
							}
						} else {
							constants.put(option, newValue);
							options.remove(option);
						}

						// Check all of the rules that affected it to see if they need to be propagated as well.
						for (RuleDefinition affectedRule : option2rules.remove(option)) {
							if (affectedRule != rule) {
								// Instead of *actually* checking, we just append it to the list to be checked later
								rulesToVisit.add(affectedRule);
							}
						}
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

		} while (changed);

		return new ProcessedRuleSet(constants, aliases, options, new ArrayList<>(activeRules));
	}
}
