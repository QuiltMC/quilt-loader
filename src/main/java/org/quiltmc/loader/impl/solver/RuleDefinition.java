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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.solver.Sat4jWrapper.Sat4jSolver;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.util.sat4j.pb.IPBSolver;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;
import org.quiltmc.loader.util.sat4j.specs.IConstr;
import org.quiltmc.loader.util.sat4j.specs.IVecInt;

/** Base rules that may be set by any of the rule defining methods in {@link RuleDefiner}. These are used to ensure we
 * don't have to double-call rules to make them define themselves in both of the sat4j passes. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
abstract class RuleDefinition {

	final Rule rule;
	final LoadOption[] options;

	private RuleDefinition(Rule rule, LoadOption[] options) {
		this.rule = rule;
		options = Arrays.copyOf(options, options.length);

		for (int i = 0; i < options.length; i++) {
			options[i] = process(options[i]);
		}

		this.options = options;
	}

	/* package-private */ static LoadOption process(LoadOption op) {
		if (op instanceof AliasedLoadOption) {
			LoadOption target = ((AliasedLoadOption) op).getTarget();
			if (target != null) {
				op = target;
			}
		}
		return op;
	}

	/* package-private */ void validateOptions(Set<LoadOption> validOptions) {
		for (LoadOption option : options) {
			if (RuleContext.isNegated(option)) {
				option = RuleContext.negate(option);
			}
			if (!validOptions.contains(option)) {
				throw new IllegalStateException("Tried to define rule " + rule.getClass() + " " + rule + " as " + this + ", but the option " + option.getClass() + " " + option + " isn't registered!");
			}
		}
	}

	/** @return True if this definition forces all of it's {@link LoadOption} values to have a constant value. */
	/* package-private */ abstract boolean isConstant();

	/* package-private */ abstract int minimum();

	/* package-private */ abstract int maximum();

	/* package-private */ abstract RuleType type();

	/** Checks to see if the given set of constants would affect this rule, and puts any newly known constants into the
	 * "newConstants" map. This should return null if there is a contradiction, or a rule definition to use instead if
	 * this can still be a valid rule. */
	/* package-private */ abstract RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants);

	private static RuleComputeResult resultForcedTrue(List<LoadOption> newOptions) {
		return resultForced(newOptions, true);
	}

	private static RuleComputeResult resultForced(List<LoadOption> newOptions, boolean value) {
		Map<LoadOption, Boolean> newConstants = new HashMap<>();
		for (LoadOption option : newOptions) {
			newConstants.put(option, value);
		}
		return new RuleComputeResult.Removed(newConstants);
	}

	protected abstract IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException;

	static final class AtLeastOneOf extends RuleDefinition {

		public AtLeastOneOf(Rule rule, LoadOption[] options) {
			super(rule, options);
		}

		@Override
		public String toString() {
			return "AtLeastOneOf " + options.length + Arrays.toString(options);
		}

		@Override
		protected IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException {
			return new IConstr[] { solver.addClause(wrapper.mapOptionsToSat4jClause(options)) };
		}

		@Override
		boolean isConstant() {
			return options.length == 1;
		}

		@Override
		int minimum() {
			return 1;
		}

		@Override
		int maximum() {
			return Integer.MAX_VALUE;
		}

		@Override
		RuleType type() {
			return RuleType.AT_LEAST;
		}

		@Override
		RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants) {
			return AtLeast.compute(1, this, currentConstants);
		}
	}

	static abstract class CountOf extends RuleDefinition {
		final int count;

		public CountOf(Rule rule, int count, LoadOption[] options) {
			super(rule, options);
			this.count = count;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " " + count + " of " + options.length + Arrays.toString(options);
		}
	}

	static final class AtLeast extends CountOf {

		public AtLeast(Rule rule, int count, LoadOption[] options) {
			super(rule, count, options);
		}

		@Override
		protected IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException {
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			if (count == 1) {
				return new IConstr[] { solver.addClause(clause) };
			}
			return new IConstr[] { solver.addAtLeast(clause, count) };
		}

		@Override
		boolean isConstant() {
			return options.length == count;
		}

		@Override
		int minimum() {
			return count;
		}

		@Override
		int maximum() {
			return Integer.MAX_VALUE;
		}

		@Override
		RuleType type() {
			return RuleType.AT_LEAST;
		}

		static RuleComputeResult compute(final int required, RuleDefinition rule,
			Function<LoadOption, Boolean> currentConstants) {

			List<LoadOption> newOptions = null;
			int nowRequired = required;

			for (int i = 0; i < rule.options.length; i++) {
				LoadOption option = rule.options[i];
				Boolean constantValue = currentConstants.apply(option);
				if (constantValue == null) {
					if (newOptions != null) {
						newOptions.add(option);
					}
					// Unchanged
					continue;
				}
				// Newly constant
				if (constantValue) {
					nowRequired--;
					if (nowRequired == 0) {
						// Every other value is ignorable
						return RuleComputeResult.TRIVIALLY_REMOVED;
					}
					// Populate the "newOptions" with this removed
				}

				// It's been set to "false", so one of the remaining options must be true
				// or it's been set to "true", and "nowRequired" was decreased, so we also don't need to include it
				if (newOptions == null) {
					// Add every previous option, but not *this* option
					newOptions = new ArrayList<>();
					for (int j = 0; j < i; j++) {
						newOptions.add(rule.options[j]);
					}
				}
			}

			if (newOptions == null) {
				if (nowRequired != required) {
					// Every option was removed by being a constant
					// but we still require some to be true!
					// Since we need SUM([nothing]) to be at least "nowRequired", this is a contradiction
					// (and "nowRequired" can't be 0 since we checked that earlier)
					return RuleComputeResult.CONTRADICTION;
				}

				if (nowRequired == rule.options.length) {
					// Nothing changed, but this rule was already constant
					return resultForcedTrue(Arrays.asList(rule.options));
				}

				// Nothing changed
				return RuleComputeResult.IDENTICAL;
			}

			if (newOptions.isEmpty()) {
				// Every option is now false!
				// Since we need SUM([nothing]) to be at least "nowRequired", this is a contradiction
				return RuleComputeResult.CONTRADICTION;
			}

			if (newOptions.size() == nowRequired) {
				// Force the remaining options to be true
				return resultForcedTrue(newOptions);
			}

			LoadOption[] options = newOptions.toArray(new LoadOption[0]);
			final RuleDefinition newDefintion;
			if (nowRequired == 1) {
				newDefintion = new AtLeastOneOf(rule.rule, options);
			} else {
				newDefintion = new AtLeast(rule.rule, nowRequired, options);
			}
			return new RuleComputeResult.Changed(newDefintion, Collections.emptyMap());
		}

		@Override
		RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants) {
			return compute(count, this, currentConstants);
		}
	}

	static final class AtMost extends CountOf {

		public AtMost(Rule rule, int count, LoadOption[] options) {
			super(rule, count, options);
			if (count == 0) {
				getClass();
			}
		}

		@Override
		protected IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException {
			return new IConstr[] { solver.addAtMost(wrapper.mapOptionsToSat4jClause(options), count) };
		}

		@Override
		boolean isConstant() {
			return options.length == 0 || count >= options.length || count == 0;
		}

		@Override
		int minimum() {
			return 0;
		}

		@Override
		int maximum() {
			return count;
		}

		@Override
		RuleType type() {
			return RuleType.AT_MOST;
		}

		@Override
		RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants) {
			return Between.compute(0, count, this, currentConstants);
		}
	}

	static final class Exactly extends CountOf {

		public Exactly(Rule rule, int count, LoadOption[] options) {
			super(rule, count, options);
		}

		@Override
		protected IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException {
			// Sat4j doesn't seem to handle exactly correctly ATM
			// however it's a non-issue, since internally it's just both atMost and atLeast anyway.
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			return new IConstr[] { solver.addAtMost(clause, count), solver.addAtLeast(clause, count) };
		}

		@Override
		boolean isConstant() {
			return options.length == count || count == 0;
		}

		@Override
		int minimum() {
			return count;
		}

		@Override
		int maximum() {
			return count;
		}

		@Override
		RuleType type() {
			return RuleType.EXACTLY;
		}

		@Override
		RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants) {
			return Between.compute(count, count, this, currentConstants);
		}
	}

	static final class Between extends RuleDefinition {
		final int min, max;

		public Between(Rule rule, int min, int max, LoadOption[] options) {
			super(rule, options);
			this.min = min;
			this.max = max;
		}

		@Override
		protected IConstr[] put(Sat4jSolver wrapper, IPBSolver solver) throws ContradictionException {
			// Sat4j doesn't seem to handle exactly correctly ATM
			// however it's a non-issue, since internally it's just both atMost and atLeast anyway.
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			return new IConstr[] { solver.addAtMost(clause, max), solver.addAtLeast(clause, min) };
		}

		@Override
		public String toString() {
			return "Between " + min + ", " + max + " of " + options.length + Arrays.toString(options);
		}

		@Override
		boolean isConstant() {
			return options.length == min || options.length <= max || max == 0;
		}

		@Override
		int minimum() {
			return min;
		}

		@Override
		int maximum() {
			return max;
		}

		@Override
		RuleType type() {
			return min == max ? RuleType.EXACTLY : RuleType.BETWEEN;
		}

		private static RuleComputeResult compute(int min, int max, RuleDefinition rule,
			Function<LoadOption, Boolean> currentConstants) {

			List<LoadOption> newOptions = null;

			// -inf...0...+LENGTH
			int newMin = min;

			// 0...+LENGTH
			int newMax = max;

			for (int i = 0; i < rule.options.length; i++) {
				LoadOption option = rule.options[i];
				Boolean constantValue = currentConstants.apply(option);
				if (constantValue == null) {
					if (newOptions != null) {
						newOptions.add(option);
					}
					// Unchanged
					continue;
				}
				// Newly constant
				if (constantValue) {
					// it's been set to "true"
					if (newMax == 0) {
						// But we can't have any more be "true", so we've hit a contradiction
						return RuleComputeResult.CONTRADICTION;
					}
					newMin--;
					newMax--;
					// Fill out the new options list, but without this
				}

				// It's been set to "false", so one of the remaining options must be true
				// or it's been set to "true", and "nowRequired" was decreased, so we also don't need to include it
				if (newOptions == null) {
					// Add every previous option, but not *this* option
					newOptions = new ArrayList<>();
					for (int j = 0; j < i; j++) {
						newOptions.add(rule.options[j]);
					}
				}
			}

			if (newOptions == null) {
				if (newMin != min && newMin != 0) {
					// Every option was removed by being a constant
					// but we still require some to be true!
					// Since we need SUM([nothing]) to be at least "newMin", this is a contradiction
					return RuleComputeResult.CONTRADICTION;
				} else if (newMin == rule.options.length) {
					// Nothing changed, but this rule was already constant
					return resultForcedTrue(Arrays.asList(rule.options));
				} else if (newMin <= 0 && newMax == rule.options.length) {
					return RuleComputeResult.TRIVIALLY_REMOVED;
				} else if (newMax == 0) {
					return resultForced(Arrays.asList(rule.options), false);
				} else {
					// Nothing was a constant, so nothing changed.
					return RuleComputeResult.IDENTICAL;
				}
			}

			if (newOptions.isEmpty()) {
				if (newMin > 0) {
					// Every option is now false!
					// Since we need SUM([nothing]) to be at least "nowRequired", this is a contradiction
					return RuleComputeResult.CONTRADICTION;
				}

				// Every option was removed, so we don't care what the maximum was
				return RuleComputeResult.TRIVIALLY_REMOVED;
			}

			if (newOptions.size() == newMin) {
				// Force the remaining options to be true
				// (This handles "max" correctly since we never decrease it separately from "min", and it starts off >=min
				return resultForcedTrue(newOptions);
			}

			if (newMax == 0) {
				// Force the remaining options to be false
				return resultForced(newOptions, false);
			}

			LoadOption[] options = newOptions.toArray(new LoadOption[0]);
			final RuleDefinition newDefintion;

			if (newMax >= options.length) {
				// We can drop the maximum, since it's implicit
				if (newMin <= 0) {
					// This doesn't affect anything
					return RuleComputeResult.TRIVIALLY_REMOVED;
				} else if (newMin == 1) {
					newDefintion = new AtLeastOneOf(rule.rule, options);
				} else {
					newDefintion = new AtLeast(rule.rule, newMin, options);
				}
			} else if (newMin == newMax) {
				newDefintion = new Exactly(rule.rule, newMin, options);
			} else {
				newDefintion = new Between(rule.rule, newMin, newMax, options);
			}
			return new RuleComputeResult.Changed(newDefintion, Collections.emptyMap());
		}

		@Override
		RuleComputeResult computeConstants(Function<LoadOption, Boolean> currentConstants) {
			return compute(min, max, this, currentConstants);
		}
	}
}
