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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A set of {@link RuleDefinition} and {@link LoadOption}s that can be solved. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class RuleSet {

	// Non-solvable parts

	/** {@link LoadOption}s which have already been "solved". */
	public final Map<LoadOption, Boolean> constants;

	/** Map of {@link LoadOption}s to "solvable" load options. This means that all the keys will have the same solved
	 * value as their value. */
	public final Map<LoadOption, LoadOption> aliases;

	// Solvable parts

	/** Every {@link LoadOption} that needs to be solved, mapped to their weight. */
	public final Map<LoadOption, Integer> options;

	private RuleSet(Map<LoadOption, Map<Rule, Integer>> options) {
		Map<LoadOption, LoadOption> outputAliases = new HashMap<>();
		Map<LoadOption, Integer> outputOptions = new HashMap<>();

		for (Map.Entry<LoadOption, Map<Rule, Integer>> entry : options.entrySet()) {
			LoadOption option = entry.getKey();
			Map<Rule, Integer> weights = entry.getValue();
			if (option instanceof AliasedLoadOption) {
				LoadOption target = ((AliasedLoadOption) option).getTarget();
				if (target != null) {
					option = target;
				}
			}

			int weight = 0;
			for (Integer value : weights.values()) {
				weight += value;
			}

			outputOptions.merge(option, weight, (a, b) -> a + b);
		}

		this.constants = Collections.emptyMap();
		this.aliases = Collections.unmodifiableMap(outputAliases);
		this.options = Collections.unmodifiableMap(outputOptions);
	}

	private RuleSet(Map<LoadOption, Boolean> constants, Map<LoadOption, LoadOption> aliases, //
		Map<LoadOption, Integer> options) {

		this.constants = constants;
		this.aliases = aliases;
		this.options = options;
	}

	public abstract boolean isFullySolved();

	public Collection<LoadOption> getConstantSolution() {
		Set<LoadOption> set = new HashSet<>();
		getConstantSolution(set);
		return set;
	}

	public void getConstantSolution(Collection<LoadOption> dst) {
		for (Map.Entry<LoadOption, Boolean> entry : constants.entrySet()) {
			if (entry.getValue()) {
				dst.add(entry.getKey());
			} else {
				dst.add(entry.getKey().negate());
			}
		}

		for (Map.Entry<LoadOption, LoadOption> entry : aliases.entrySet()) {
			LoadOption alias = entry.getKey();
			LoadOption target = entry.getValue();
			if (constants.get(target)) {
				dst.add(alias);
			} else {
				dst.add(alias.negate());
			}
		}
	}

	public abstract void forEachRule(Consumer<RuleDefinition> consumer);

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class InputRuleSet extends RuleSet {

		public final Map<Rule, List<RuleDefinition>> ruleToDefinitions;

		public InputRuleSet(Map<LoadOption, Map<Rule, Integer>> options, //
			Map<Rule, List<RuleDefinition>> ruleToDefinitions) {
			super(options);
			this.ruleToDefinitions = ruleToDefinitions;
		}

		@Override
		public boolean isFullySolved() {
			return options.isEmpty() && ruleToDefinitions.isEmpty();
		}

		@Override
		public void forEachRule(Consumer<RuleDefinition> consumer) {
			for (List<RuleDefinition> list : ruleToDefinitions.values()) {
				list.forEach(consumer);
			}
		}
	}

	/** A {@link RuleSet} where the input {@link RuleDefinition}s cannot be easily mapped back to the original
	 * {@link Rule}s. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class ProcessedRuleSet extends RuleSet {

		/** Every active {@link RuleDefinition} that influences the load options chosen. */
		public final List<RuleDefinition> rules;

		ProcessedRuleSet(Map<LoadOption, Boolean> constants, Map<LoadOption, LoadOption> aliases, //
			Map<LoadOption, Integer> options, List<RuleDefinition> rules) {

			super(constants, aliases, options);
			this.rules = rules;
		}

		@Override
		public boolean isFullySolved() {
			return options.isEmpty() && rules.isEmpty();
		}

		@Override
		public void forEachRule(Consumer<RuleDefinition> consumer) {
			rules.forEach(consumer);
		}
	}
}
