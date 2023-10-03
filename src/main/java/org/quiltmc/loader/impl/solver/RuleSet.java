package org.quiltmc.loader.impl.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A set of {@link RuleDefinition} and {@link LoadOption}s that can be solved. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class RuleSet {

	// Non-solvable parts

	/** {@link LoadOption}s which have already been "solved". */
	public final Map<LoadOption, Boolean> constants;

	/** Map of {@link LoadOption}s to "solvable" load options. This means that all the keys will have the same solved
	 * value as their value. */
	public final Map<LoadOption, LoadOption> aliases;

	// Solvable parts

	/** Every {@link LoadOption} that needs to be solved, mapped to their weight. */
	public final Map<LoadOption, Integer> options;

	/** Every active {@link RuleDefinition} that influences the load options chosen. */
	public final List<RuleDefinition> rules;

	public RuleSet(Map<LoadOption, Map<Rule, Integer>> options, List<RuleDefinition> rules) {
		Map<LoadOption, LoadOption> outputAliases = new HashMap<>();
		Map<LoadOption, Integer> outputOptions = new HashMap<>();
		List<RuleDefinition> outputRules = new ArrayList<>();

		for (Map.Entry<LoadOption, Map<Rule, Integer>> entry : options.entrySet()) {
			LoadOption option = entry.getKey();
			Map<Rule, Integer> weights = entry.getValue();
			Set<LoadOption> seen = new LinkedHashSet<>();
			while (option instanceof AliasedLoadOption) {
				LoadOption to = ((AliasedLoadOption) option).getTarget();
				if (to == null) {
					break;
				} else if (seen.add(to)) {
					outputAliases.put(option, to);
					option = to;
				} else {
					throw new IllegalStateException("Looping alias " + seen);
				}
			}

			int weight = 0;
			for (Integer value : weights.values()) {
				weight += value;
			}

			outputOptions.put(option, weight);
		}

		outputRules.addAll(rules);

		this.constants = Collections.emptyMap();
		this.aliases = Collections.unmodifiableMap(outputAliases);
		this.options = Collections.unmodifiableMap(outputOptions);
		this.rules = Collections.unmodifiableList(outputRules);
	}

	RuleSet(Map<LoadOption, Boolean> constants, Map<LoadOption, LoadOption> aliases, Map<LoadOption, Integer> options,
		List<RuleDefinition> rules) {
		this.constants = constants;
		this.aliases = aliases;
		this.options = options;
		this.rules = rules;
	}
}
