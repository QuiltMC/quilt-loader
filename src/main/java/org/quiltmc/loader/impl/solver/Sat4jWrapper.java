/*
 * Copyright 2016 FabricMC
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
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
class Sat4jWrapper implements RuleContext {

	private static final boolean LOG = Boolean.getBoolean(SystemProperties.DEBUG_MOD_SOLVING);

	public enum Sat4jSolveStep {

		DEFINE(true),
		SOLVE(true),
		RE_SOLVING(false),
		OPTIMISE(false),
		DONE(false);

		final boolean canAdd;

		Sat4jSolveStep(boolean canAdd) {
			this.canAdd = canAdd;
		}
	}

	private final LogCategory CATEGORY = new LogCategory("Sat4j");

	private volatile Sat4jSolveStep step = Sat4jSolveStep.DEFINE;

	/** Only available during {@link Sat4jSolveStep#SOLVE} */
	private XplainPB explainer;

	/** Only available during {@link Sat4jSolveStep#OPTIMISE} */
	private volatile PseudoOptDecorator optimiser;

	/** Set to {@link #explainer} during {@link Sat4jSolveStep#SOLVE}, and {@link #optimiser} during
	 * {@link Sat4jSolveStep#OPTIMISE}. */
	private IPBSolver solver;

	private boolean rulesChanged = false;

	private volatile boolean cancelled = false;

	private final Map<LoadOption, Integer> optionToWeight = new HashMap<>();
	private final Map<Rule, List<RuleDefinition>> ruleToDefinitions = new HashMap<>();

	private final Map<LoadOption, Integer> optionToIndex = new HashMap<>();
	private final Map<Integer, LoadOption> indexToOption = new HashMap<>();

	/** Only available during {@link Sat4jSolveStep#SOLVE}. */
	private Map<IConstr, Rule> constraintToRule = null;

	public Sat4jWrapper() {
	}

	public Sat4jSolveStep getStep() {
		return step;
	}

	// ############
	// # Defining #
	// ############

	/** Adds a new {@link LoadOption}, without any weight. */
	@Override
	public void addOption(LoadOption option) {
		addOption(option, 0);
	}

	/** Adds a new {@link LoadOption}, with the given weight. */
	@Override
	public void addOption(LoadOption option, int weight) {
		validateCanAdd();
		optionToWeight.put(option, weight);

		if (LOG) {
			Log.info(CATEGORY, "Adding option " + option + " with weight " + weight);
		}

		List<Rule> rulesToRedefine = new ArrayList<>();

		for (Rule rule : ruleToDefinitions.keySet()) {
			if (rule.onLoadOptionAdded(option)) {
				rulesToRedefine.add(rule);
			}
		}

		for (Rule rule : rulesToRedefine) {
			redefine(rule);
		}
	}

	@Override
	public void setWeight(LoadOption option, int weight) {
		validateCanAdd();
		optionToWeight.put(option, weight);
	}

	@Override
	public void removeOption(LoadOption option) {
		validateCanAdd();

		if (LOG) {
			Log.info(CATEGORY, "Removing option " + option);
		}

		indexToOption.remove(optionToIndex.remove(option));
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

		validateCanAdd();

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

		validateCanAdd();
		ruleToDefinitions.remove(rule);
		rulesChanged = true;
	}

	/** Clears any current definitions this rule is associated with, and calls {@link Rule#define(RuleDefiner)} */
	@Override
	public void redefine(Rule rule) {

		if (LOG) {
			Log.info(CATEGORY, "Redefining rule " + rule);
		}

		validateCanAdd();
		ruleToDefinitions.put(rule, new ArrayList<>(1));
		rulesChanged = true;
		rule.define(new RuleDefinerInternal(rule));
	}

	private void validateCanAdd() {
		if (!getStep().canAdd) {
			throw new IllegalStateException("Cannot add new options/rules during " + getStep());
		}
	}

	// ###########
	// # Solving #
	// ###########

	/** Attempts to find a solution. This should be called during either definition or solving. If this returns true
	 * then the {@link #getStep()} will be moved to {@link Sat4jSolveStep#RE_SOLVING}.
	 * 
	 * @return True if a solution could be found, or false if one could not. */
	public boolean hasSolution() throws TimeoutException {

		checkCancelled();

		if (step == Sat4jSolveStep.DEFINE || (step == Sat4jSolveStep.SOLVE && rulesChanged)) {

			if (LOG) {
				if (step != Sat4jSolveStep.DEFINE) {
					Log.info(CATEGORY, "Redefining rules");
				}
			}

			rulesChanged = false;
			optionToIndex.clear();
			indexToOption.clear();
			constraintToRule = new HashMap<>();
			solver = SolverFactory.newDefault();
			solver = explainer = new XplainPB(solver);
			putDefinitions();

		} else if (step != Sat4jSolveStep.SOLVE) {
			throw new IllegalStateException("Wrong step to call findSolution! (" + step + ")");
		}
		step = Sat4jSolveStep.SOLVE;

		boolean success = explainer.isSatisfiable();

		if (success) {
			if (LOG) {
				Log.info(CATEGORY, "Found a valid solution, preparing to optimise it.");
			}

			explainer = null;
			solver = new OptToPBSATAdapter(optimiser = new PseudoOptDecorator(SolverFactory.newDefault()));
//			optimiser.setTimeoutForFindingBetterSolution(2);
			step = Sat4jSolveStep.RE_SOLVING;
			optionToIndex.clear();
			indexToOption.clear();
			solver.setVerbose(true);
			constraintToRule = null;
			putDefinitions();
			return true;
		} else {
			return false;
		}
	}

	/** @return The error that prevented {@link #hasSolution()} from returning true.
	 * @throws IllegalStateException if the last call to {@link #hasSolution()} didn't return false, or if any other
	 *             methods have been called since the last call to {@link #hasSolution()}. */
	public Collection<Rule> getError() throws TimeoutException {
		checkCancelled();

		Collection<IConstr> constraints = explainer.explain();
		Set<Rule> rules = new HashSet<>();

		for (IConstr c : constraints) {
			rules.add(constraintToRule.get(c));
		}

		return rules;
	}

	/** Computes and returns the optimised solution.
	 * 
	 * @return The solution.
	 * @throws TimeoutException if the optimisation was cancelled before it completed. This will only be thrown if it
	 *             hasn't computed any solutions when it is cancelled.
	 * @throws IllegalStateException if this is not in the {@link Sat4jSolveStep#RE_SOLVING} step. */
	public List<LoadOption> getSolution() throws TimeoutException, ModSolvingError {
		checkCancelled();

		if (LOG) {
			Log.info(CATEGORY, "Starting optimisation.");
		}

		int count = 0;
		boolean success = false;

		// 5 second timeout - this will regularly be hit by users
		// as such this needs to be fairly short, but not too short as then there's no time to optimise.
		// ALSO this happens *every cycle*
		optimiser.setTimeoutForFindingBetterSolution(5);

		while (true) {

			try {
				if (!optimiser.admitABetterSolution()) {
					break;
				}
			} catch (TimeoutException e) {
				if (success) {
					if (LOG) {
						Log.info(CATEGORY, "Aborted optimisation due to timeout");
					}
					break;
				}
			}

			step = Sat4jSolveStep.OPTIMISE;
			success = true;

			if (LOG) {
				Log.info(CATEGORY, "Found solution #" + (++count) + " weight = " + optimiser.calculateObjective().intValue() + " = " + Arrays.toString(optimiser.model()));
			}

			try {
				optimiser.discardCurrentSolution();
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

		int[] model = optimiser.model();
		List<LoadOption> list = new ArrayList<>();

		for (int value : model) {
			if (value < 0) {
				// Negated, so ignored
				continue;
			}

			LoadOption option = indexToOption.get(value);
			if (option == null) {
				throw new ModSolvingError("Unknown value " + value);
			}
			list.add(option);
		}

		step = Sat4jSolveStep.DONE;

		return list;
	}

	/** This method cancels the current operation, if there is one running. */
	public boolean cancel() {
		IPBSolver s = solver;
		if (s != null) {
			s.expireTimeout();
			return true;
		}
		return false;
	}

	/** This method cancels the current operation, if we are in the given step, otherwise this does nothing. */
	public boolean cancelIf(Sat4jSolveStep step) {
		IPBSolver s = solver;
		if (s != null && this.step == step) {
			s.expireTimeout();
			return true;
		}
		return false;
	}

	/** Cancels any current and future operation. */
	public void hardCancel() {
		cancelled = true;
		cancel();
	}

	// ############
	// # Internal #
	// ############

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

	private void checkCancelled() throws TimeoutException {
		if (cancelled) {
			throw new TimeoutException();
		}
	}

	private void putDefinitions() {
		if (constraintToRule != null) {
			constraintToRule.clear();
		}

		for (LoadOption option : optionToWeight.keySet()) {
			putOptionRaw(option);
		}

		for (Map.Entry<Rule, List<RuleDefinition>> entry : ruleToDefinitions.entrySet()) {
			Rule rule = entry.getKey();

			for (RuleDefinition def : entry.getValue()) {
				addRuleDefinition(rule, def);
			}
		}

		if (optimiser != null) {
			int count = optionToWeight.size();
			IVecInt vars = new VecInt(count);
			IVec<BigInteger> coeffs = new Vec<>(count);

			for (Map.Entry<LoadOption, Integer> entry : optionToWeight.entrySet()) {
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

	private void addRuleDefinition(Rule rule, RuleDefinition def) {

		IConstr[] added;
		try {
			added = def.put(this, solver);
		} catch (ContradictionException e) {
			// Should never happen
			throw new IllegalStateException("Failed to add the definition " + def, e);
		}

		if (constraintToRule != null) {
			for (IConstr c : added) {
				if (c != null) {
					constraintToRule.put(c, rule);
				}
			}
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

		private void rule(RuleDefinition def) {
			validateCanAdd();

			ruleToDefinitions.computeIfAbsent(rule, r -> new ArrayList<>()).add(def);

			if (solver != null) {
				addRuleDefinition(rule, def);
			}
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
}
