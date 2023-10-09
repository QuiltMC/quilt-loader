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

package org.quiltmc.loader.impl.solver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.solver.RuleSet.InputRuleSet;
import org.quiltmc.loader.impl.solver.RuleSet.ProcessedRuleSet;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.util.sat4j.core.Vec;
import org.quiltmc.loader.util.sat4j.core.VecInt;
import org.quiltmc.loader.util.sat4j.pb.IPBSolver;
import org.quiltmc.loader.util.sat4j.pb.ObjectiveFunction;
import org.quiltmc.loader.util.sat4j.pb.OptToPBSATAdapter;
import org.quiltmc.loader.util.sat4j.pb.PseudoOptDecorator;
import org.quiltmc.loader.util.sat4j.pb.SolverFactory;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;
import org.quiltmc.loader.util.sat4j.pb.tools.XplainPB;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;
import org.quiltmc.loader.util.sat4j.specs.IConstr;
import org.quiltmc.loader.util.sat4j.specs.IVec;
import org.quiltmc.loader.util.sat4j.specs.IVecInt;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;

/** A wrapper around sat4j. We use this instead of {@link DependencyHelper} since that's a bit more limited.
 * <p>
 * Solving happens in x stages:
 * <ol>
 * <li>Create the solver</li>
 * <li>Define rules in the solver</li>
 * <li>Attempt to solve the rules.</li>
 * <li>Perform optimisation of the rules.</li>
 * </ol>
 * This is (mostly) separated from any more specific rules */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class Sat4jWrapper implements RuleContext {

	private static final boolean LOG = Boolean.getBoolean(SystemProperties.DEBUG_MOD_SOLVING);
	private static final boolean PRINT_RESULTS = Boolean.getBoolean(SystemProperties.PRINT_MOD_SOLVING_RESULTS);
	static final LogCategory CATEGORY = LogCategory.create("Sat4j");

	private volatile boolean cancelled = false;

	private final Map<LoadOption, Map<Rule, Integer>> optionToWeight = new HashMap<>();
	private final Map<Rule, List<RuleDefinition>> ruleToDefinitions = new HashMap<>();

	private CalculationStage stage = new DefineStage();

	public Sat4jWrapper() {}

	/** Clears out this {@link Sat4jWrapper} of all data EXCEPT the added {@link Rule}s and {@link LoadOption}s. */
	public void resetStage() {
		cancelled = false;
		stage = new DefineStage();
	}

	// ############
	// # Defining #
	// ############

	@Override
	public void addOption(LoadOption option) {
		optionToWeight.put(option, new HashMap<>());

		if (LOG) {
			Log.info(CATEGORY, "Adding option " + option);
		}

		List<Rule> rulesToRedefine = new ArrayList<>();

		for (Rule rule : ruleToDefinitions.keySet()) {
			if (rule.onLoadOptionAdded(option)) {
				rulesToRedefine.add(rule);
			}
		}

		if (LOG) {
			Log.info(CATEGORY, "Finished adding option " + option);
		}

		for (Rule rule : rulesToRedefine) {
			redefine(rule);
		}
	}

	@Override
	public void setWeight(LoadOption option, Rule key, int weight) {
		if (option instanceof AliasedLoadOption) {
			LoadOption target = ((AliasedLoadOption) option).getTarget();
			if (target != null) {
				option = target;
			}
		}
		Map<Rule, Integer> weightMap = optionToWeight.get(option);
		if (weightMap != null) {
			weightMap.put(key, weight);
		} else {
			throw new IllegalArgumentException("Unknown LoadOption " + option);
		}
	}

	@Override
	public void removeOption(LoadOption option) {
		if (LOG) {
			Log.info(CATEGORY, "Removing option " + option);
		}

		optionToWeight.remove(option);

		List<Rule> rulesToRedefine = new ArrayList<>();

		for (Rule rule : ruleToDefinitions.keySet()) {
			if (rule.onLoadOptionRemoved(option)) {
				rulesToRedefine.add(rule);
			}
		}

		for (Rule rule : rulesToRedefine) {
			redefine(rule);
		}
	}

	/** Adds a new {@link Rule} to this solver. This calls {@link Rule#onLoadOptionAdded(LoadOption)} for every
	 * {@link LoadOption} currently held, and calls {@link Rule#define(RuleDefiner)} once afterwards. */
	@Override
	public void addRule(Rule rule) {
		if (LOG) {
			Log.info(CATEGORY, "Added rule " + rule);
		}

		ruleToDefinitions.put(rule, new ArrayList<>(1));

		for (LoadOption option : optionToWeight.keySet()) {
			rule.onLoadOptionAdded(option);
		}

		rule.define(new RuleDefinerInternal(rule));
	}

	public void removeRule(Rule rule) {
		if (LOG) {
			Log.info(CATEGORY, "Removed rule " + rule);
		}

		ruleToDefinitions.remove(rule);
		stage = stage.onChange();
	}

	/** Clears any current definitions this rule is associated with, and calls {@link Rule#define(RuleDefiner)} sometime
	 * before solving. */
	@Override
	public void redefine(Rule rule) {

		if (LOG) {
			Log.info(CATEGORY, "Redefining rule " + rule);
		}

		ruleToDefinitions.put(rule, new ArrayList<>(1));
		rule.define(new RuleDefinerInternal(rule));
		stage = stage.onChange();
	}

	public static boolean isNegated(LoadOption option) {
		return option instanceof NegatedLoadOption;
	}

	public static LoadOption negate(LoadOption option) {
		if (option instanceof NegatedLoadOption) {
			return ((NegatedLoadOption) option).not;
		} else {
			return new NegatedLoadOption(option);
		}
	}

	// ###########
	// # Solving #
	// ###########

	/** Attempts to find a solution. This should be called during either definition or solving. If this returns true
	 * then the {@link #getStep()} will be moved to {@link Sat4jSolveStep#RE_SOLVING}.
	 * 
	 * @return True if a solution could be found, or false if one could not. */
	public boolean hasSolution() throws TimeoutException, ModSolvingError {
		checkCancelled();
		return stage.hasSolution();
	}

	/** @return The error that prevented {@link #hasSolution()} from returning true.
	 * @throws IllegalStateException if the last call to {@link #hasSolution()} didn't return false, or if any other
	 *             methods have been called since the last call to {@link #hasSolution()}. */
	public Collection<Rule> getError() throws TimeoutException {
		checkCancelled();
		return stage.getError();
	}

	/** Computes and returns the optimised solution.
	 * 
	 * @return The solution.
	 * @throws TimeoutException if the optimisation was cancelled before it completed. This will only be thrown if it
	 *             hasn't computed any solutions when it is cancelled.
	 * @throws IllegalStateException if {@link #hasSolution()} didn't just return true, or if any other methods have
	 *             been called since the last call to {@link #hasSolution()}. */
	public Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
		checkCancelled();
		Collection<LoadOption> solution = stage.getSolution();
		if (PRINT_RESULTS) {
			Log.info(CATEGORY, "Final solution:");
			for (LoadOption option : solution) {
				Log.info(CATEGORY, option.toString());
			}
		}
		return solution;
	}

	/** Cancels any current and future operation. */
	public void cancel() {
		cancelled = true;
	}

	// ############
	// # Internal #
	// ############

	private void checkCancelled() throws TimeoutException {
		if (cancelled) {
			throw new TimeoutException();
		}
	}

	class RuleDefinerInternal implements RuleDefiner {

		final Rule rule;

		RuleDefinerInternal(Rule rule) {
			this.rule = rule;
		}

		@Override
		public LoadOption negate(LoadOption option) {
			if (option instanceof NegatedLoadOption) {
				return ((NegatedLoadOption) option).not;
			}

			return new NegatedLoadOption(RuleDefinition.process(option));
		}

		@Override
		public LoadOption[] deduplicate(LoadOption... options) {
			Map<LoadOption, String> set = new IdentityHashMap<>();
			List<LoadOption> dst = new ArrayList<>(options.length);
			for (LoadOption option : options) {
				if (option instanceof AliasedLoadOption) {
					option = ((AliasedLoadOption) option).getTarget();
				}

				if (set.put(option, "") == null) {
					dst.add(option);
				}
			}
			return dst.toArray(new LoadOption[0]);
		}

		private void rule(RuleDefinition def) {
			if (LOG) {
				Log.info(CATEGORY, "Rule " + def);
			}

			ruleToDefinitions.computeIfAbsent(rule, r -> new ArrayList<>()).add(def);
		}

		@Override
		public void atLeastOneOf(LoadOption... options) {
			if (options.length == 0) {
				throw new IllegalArgumentException("Cannot define 'atLeastOneOf' with an empty options array!");
			}
			rule(new RuleDefinition.AtLeastOneOf(rule, options));
		}

		@Override
		public void atLeast(int count, LoadOption... options) {
			if (options.length < count) {
				throw new IllegalArgumentException(
					"Cannot define 'atLeast(" + count + ")' with a smaller options array!\n" + Arrays.toString(options)
				);
			}
			rule(new RuleDefinition.AtLeast(rule, count, options));
		}

		@Override
		public void atMost(int count, LoadOption... options) {
			if (count < 0) {
				throw new IllegalArgumentException("Cannot define 'atMost(" + count + ")' with a negative count!");
			}
			rule(new RuleDefinition.AtMost(rule, count, options));
		}

		@Override
		public void exactly(int count, LoadOption... options) {
			if (options.length < count) {
				throw new IllegalArgumentException(
					"Cannot define 'exactly(" + count + ")' with a smaller options array!\n" + Arrays.toString(options)
				);
			}
			rule(new RuleDefinition.Exactly(rule, count, options));
		}

		@Override
		public void between(int min, int max, LoadOption... options) {
			if (options.length < min) {
				throw new IllegalArgumentException(
					"Cannot define 'between(" + min + ", " + max + ")' with a smaller options array!\n" + Arrays
						.toString(options)
				);
			}
			if (max < min) {
				throw new IllegalArgumentException(
					"Cannot define 'between(" + min + ", " + max + ")' with a max lower than min!"
				);
			}
			rule(new RuleDefinition.Between(rule, min, max, options));
		}
	}

	/** Contains the actual Sat4j fields for computation. */
	/* package-private */ static abstract class Sat4jSolver {

		final IPBSolver solver;
		final RuleSet inputRules;

		final Map<LoadOption, Integer> optionToIndex = new HashMap<>();
		final Map<Integer, LoadOption> indexToOption = new HashMap<>();

		public Sat4jSolver(IPBSolver solver, RuleSet rules) {
			this.solver = solver;
			this.inputRules = rules;

			for (LoadOption option : rules.options.keySet()) {
				putOptionRaw(option);
			}
		}

		/* package-private */ IVecInt mapOptionsToSat4jClause(LoadOption[] options) {
			IVecInt vec = new VecInt(options.length);

			for (LoadOption option : options) {
				boolean negated = false;

				if (option instanceof NegatedLoadOption) {
					negated = true;
					option = ((NegatedLoadOption) option).not;
				}

				int value = putOptionRaw(option);

				if (negated) {
					value = -value;
				}

				vec.push(value);
			}

			return vec;
		}

		private int putOptionRaw(LoadOption option) {
			Integer objVal = optionToIndex.get(option);

			if (objVal == null) {
				objVal = solver.nextFreeVarId(true);
				optionToIndex.put(option, objVal);
				indexToOption.put(objVal, option);

				if (LOG) {
					Log.info(CATEGORY, objVal + " = " + option);
				}
			}

			int value = objVal;
			return value;
		}

		private IConstr[] addRuleDefinition(RuleDefinition def) {

			def.validateOptions(optionToIndex.keySet());

			IConstr[] constraints;
			try {
				constraints = def.put(this, solver);
			} catch (ContradictionException e) {
				// Should never happen
				throw new IllegalStateException("Failed to add the definition " + def, e);
			}

			return constraints;
		}
	}

	private static final class Sat4jSolverSatisfiable extends Sat4jSolver {

		private final XplainPB explainer;

		private final Map<IConstr, Rule> constraintToRule = new HashMap<>();

		public Sat4jSolverSatisfiable(InputRuleSet rules) {
			super(new XplainPB(SolverFactory.newDefault()), rules);
			this.explainer = (XplainPB) solver;

			for (Map.Entry<Rule, List<RuleDefinition>> entry : rules.ruleToDefinitions.entrySet()) {
				Rule rule = entry.getKey();

				for (RuleDefinition def : entry.getValue()) {
					for (IConstr constraint : super.addRuleDefinition(def)) {
						if (constraint != null) {
							constraintToRule.put(constraint, rule);
						}
					}
				}
			}
		}
	}

	private static final class Sat4jSolverOptimizer extends Sat4jSolver {

		/** Only available during {@link Sat4jSolveStep#OPTIMISE} */
		private final PseudoOptDecorator optimiser;

		public Sat4jSolverOptimizer(RuleSet rules) {
			super(new OptToPBSATAdapter(new PseudoOptDecorator(SolverFactory.newDefault())), rules);
			this.optimiser = (PseudoOptDecorator) ((OptToPBSATAdapter) solver).decorated();

			rules.forEachRule(super::addRuleDefinition);

			int count = rules.options.size();
			IVecInt vars = new VecInt(count);
			IVec<BigInteger> coeffs = new Vec<>(count);

			for (Map.Entry<LoadOption, Integer> entry : rules.options.entrySet()) {
				Integer value = optionToIndex.get(entry.getKey());
				if (value == null) {
					throw new NullPointerException(entry.getKey() + " isn't in the optionToIndex map!");
				}
				vars.push(value);
				coeffs.push(BigInteger.valueOf(entry.getValue()));
			}

			optimiser.setObjectiveFunction(new ObjectiveFunction(vars, coeffs));
		}
	}

	private abstract class CalculationStage {

		abstract CalculationStage onChange();

		abstract boolean hasSolution() throws TimeoutException, ModSolvingError;

		abstract Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError;

		abstract Collection<Rule> getError() throws TimeoutException;
	}

	private final class DefineStage extends CalculationStage {

		@Override
		CalculationStage onChange() {
			return this;
		}

		@Override
		boolean hasSolution() throws TimeoutException, ModSolvingError {

			InputRuleSet originalRules = new InputRuleSet(optionToWeight, ruleToDefinitions);
			Sat4jSolverSatisfiable solver = new Sat4jSolverSatisfiable(originalRules);
			boolean success = solver.solver.isSatisfiable();

			if (success) {
				if (PRINT_RESULTS) {
					Log.info(CATEGORY, "Found a valid solution, preparing to optimise it.");
				}

				final RuleSet toOptimize;
				final boolean ENABLE_PRE_PROCESS = true;
				if (ENABLE_PRE_PROCESS) {
					int ruleCount = 0;
					for (List<RuleDefinition> defs : originalRules.ruleToDefinitions.values()) {
						ruleCount += defs.size();
					}
					if (PRINT_RESULTS) {
						Log.info(CATEGORY, "Pre-processing " + ruleCount + " rules and " + originalRules.options.size() + " options");
					}
					ProcessedRuleSet processed;
					try {
						processed = SolverPreProcessor.preProcess(originalRules);
					} catch (ContradictionException e) {
						// Should never happen, since we just validated the solution
						// (It means there's a bug in the pre-processor)
						// TODO: Collect the rules and store them in a reasonable format to use for reproduction!
						throw new ModSolvingError("Failed to pre-process rule set " + originalRules, e);
					}

					if (processed.isFullySolved()) {
						if (PRINT_RESULTS) {
							Log.info(CATEGORY, "Fully solved solution via pre-processer");
						}
						stage = new SolvedStage(processed.getConstantSolution());
						return true;
					}

					if (PRINT_RESULTS) {
						Log.info(CATEGORY, "Partially solved solution via pre-processer, continuing to optimisation");
						Log.info(CATEGORY, " -> " + processed.rules.size() + " rules, " + processed.options.size() + " options");
						if (processed.rules.isEmpty()) {
							Log.info(CATEGORY, " ! 0 rules left, but still have the following options:");
							for (LoadOption option : processed.options.keySet()) {
								Log.info(CATEGORY, " left: " + option.toString());
							}
						}
						Log.info(CATEGORY, "Constant values:");
						for (LoadOption option : processed.getConstantSolution()) {
							Log.info(CATEGORY, option.toString());
						}
						Log.info(CATEGORY, "Unknown values:");
						List<String> list = new ArrayList<>();
						for (LoadOption option : processed.options.keySet()) {
							list.add(option.toString());
						}
						list.sort(null);
						list.forEach(item -> Log.info(CATEGORY, item));
						list.clear();
						Log.info(CATEGORY, "Remaining rules:");
						for (RuleDefinition def : processed.rules) {
							list.add(def.toString());
						}
						list.sort(null);
						list.forEach(item -> Log.info(CATEGORY, item));
						list.clear();
					}
					toOptimize = processed;
				} else {
					toOptimize = originalRules;
				}

				Sat4jSolverOptimizer optimizer = new Sat4jSolverOptimizer(toOptimize);
				stage = new OptimizationStage(optimizer);
				return true;
			} else {
				Collection<IConstr> constraints = solver.explainer.explain();
				Set<Rule> error = new HashSet<>();

				for (IConstr c : constraints) {
					error.add(solver.constraintToRule.get(c));
				}

				stage = new ErrorStage(error);
				return false;
			}
		}

		@Override
		Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
			throw new IllegalStateException("Call hasSolution first!");
		}

		@Override
		Collection<Rule> getError() throws TimeoutException {
			throw new IllegalStateException("Call hasSolution first!");
		}
	}

	private final class ErrorStage extends CalculationStage {

		private final Collection<Rule> error;

		ErrorStage(Collection<Rule> error) {
			this.error = error;
		}

		@Override
		CalculationStage onChange() {
			return new DefineStage();
		}

		@Override
		boolean hasSolution() throws TimeoutException {
			return false;
		}

		@Override
		Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
			throw new IllegalStateException("hasSolution() returned false, so there is no solution!");
		}

		@Override
		Collection<Rule> getError() throws TimeoutException {
			return error;
		}
	}

	/** Used when we have validated that a rule set contains valid entries, and just needs to be optimised */
	private final class OptimizationStage extends CalculationStage {

		final Sat4jSolverOptimizer optimiser;

		OptimizationStage(Sat4jSolverOptimizer optimiser) {
			this.optimiser = optimiser;
		}

		@Override
		CalculationStage onChange() {
			return new DefineStage();
		}

		@Override
		boolean hasSolution() throws TimeoutException {
			return true;
		}

		@Override
		Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
			checkCancelled();

			if (PRINT_RESULTS) {
				Log.info(CATEGORY, "Starting optimisation.");
			}

			int count = 0;
			boolean success = false;

			// 5 second timeout - this will regularly be hit by users
			// as such this needs to be fairly short, but not too short as then there's no time to optimise.
			// ALSO this happens *every cycle*
			optimiser.optimiser.setTimeoutForFindingBetterSolution(5);

			while (true) {

				try {
					if (!optimiser.optimiser.admitABetterSolution()) {
						break;
					}
				} catch (TimeoutException e) {
					if (success) {
						if (PRINT_RESULTS) {
							Log.info(CATEGORY, "Aborted optimisation due to timeout");
						}
						break;
					}
				}

				success = true;

				if (PRINT_RESULTS) {
					Log.info(
						CATEGORY, "Found solution #" + (++count) + " weight = " + optimiser.optimiser.calculateObjective()
							.intValue() + " = " + Arrays.toString(optimiser.optimiser.model())
					);
				}

				try {
					optimiser.optimiser.discardCurrentSolution();
				} catch (ContradictionException e) {
					// This means we're *already* optimal?
					if (LOG) {
						Log.info(CATEGORY, "Found optimal solution!");
					}
					break;
				}
			}

			if (!success) {
				throw new ModSolvingError(
					"We just solved this! Something must have gone wrong internally..." + ruleToDefinitions
				);
			}

			int[] model = optimiser.optimiser.model();
			List<LoadOption> list = new ArrayList<>();
			optimiser.inputRules.getConstantSolution(list);

			for (int value : model) {
				if (value < 0) {
					// Negated, so ignored
					continue;
				}

				LoadOption option = optimiser.indexToOption.get(value);
				if (option == null) {
					throw new ModSolvingError("Unknown value " + value);
				}
				list.add(option);
			}

			stage = new SolvedStage(list);
			return list;
		}

		@Override
		Collection<Rule> getError() throws TimeoutException {
			throw new IllegalStateException("hasSolution() returned true, so there is no error!");
		}
	}

	/** Indicates that the {@link SolverPreProcessor} was able to fully solve the rules, or we've fully optimised the
	 * solution through Sat4j. */
	private final class SolvedStage extends CalculationStage {

		final Collection<LoadOption> solution;

		SolvedStage(RuleSet complete) {
			Set<LoadOption> set = new HashSet<>();
			complete.getConstantSolution(set);

			this.solution = Collections.unmodifiableSet(set);
		}

		SolvedStage(Collection<LoadOption> solution) {
			this.solution = Collections.unmodifiableCollection(solution);
		}

		@Override
		CalculationStage onChange() {
			return new DefineStage();
		}

		@Override
		boolean hasSolution() throws TimeoutException {
			return true;
		}

		@Override
		Collection<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
			return solution;
		}

		@Override
		Collection<Rule> getError() throws TimeoutException {
			throw new IllegalStateException("hasSolution() returned true, so there is no error!");
		}
	}
}
