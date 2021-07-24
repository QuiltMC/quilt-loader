package org.quiltmc.loader.impl.solver;

import java.util.Arrays;

import org.quiltmc.loader.util.sat4j.pb.IPBSolver;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;
import org.quiltmc.loader.util.sat4j.specs.IConstr;
import org.quiltmc.loader.util.sat4j.specs.IVecInt;

/** Base rules that may be set by any of the rule defining methods in {@link RuleDefiner}. These are used to ensure we
 * don't have to double-call rules to make them define themselves in both of the sat4j passes. */
abstract class RuleDefinition {

	final ModLink rule;
	final LoadOption[] options;

	public RuleDefinition(ModLink rule, LoadOption[] options) {
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

	protected abstract IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException;

	static final class AtLeastOneOf extends RuleDefinition {

		public AtLeastOneOf(ModLink rule, LoadOption[] options) {
			super(rule, options);
		}

		@Override
		public String toString() {
			return "AtLeastOneOf " + Arrays.toString(options);
		}

		@Override
		protected IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException {
			return new IConstr[] { solver.addClause(wrapper.mapOptionsToSat4jClause(options)) };
		}
	}

	static abstract class CountOf extends RuleDefinition {
		final int count;

		public CountOf(ModLink rule, int count, LoadOption[] options) {
			super(rule, options);
			this.count = count;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + " " + count + " " + Arrays.toString(options);
		}
	}

	static final class AtLeast extends CountOf {

		public AtLeast(ModLink rule, int count, LoadOption[] options) {
			super(rule, count, options);
		}

		@Override
		protected IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException {
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			if (count == 1) {
				return new IConstr[] { solver.addClause(clause) };
			}
			return new IConstr[] { solver.addAtLeast(clause, count) };
		}
	}

	static final class AtMost extends CountOf {

		public AtMost(ModLink rule, int count, LoadOption[] options) {
			super(rule, count, options);
		}

		@Override
		protected IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException {
			return new IConstr[] { solver.addAtMost(wrapper.mapOptionsToSat4jClause(options), count) };
		}
	}

	static final class Exactly extends CountOf {

		public Exactly(ModLink rule, int count, LoadOption[] options) {
			super(rule, count, options);
		}

		@Override
		protected IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException {
			// Sat4j doesn't seem to handle exactly correctly ATM
			// however it's a non-issue, since internally it's just both atMost and atLeast anyway.
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			return new IConstr[] { solver.addAtMost(clause, count), solver.addAtLeast(clause, count) };
		}
	}

	static final class Between extends RuleDefinition {
		final int min, max;

		public Between(ModLink rule, int min, int max, LoadOption[] options) {
			super(rule, options);
			this.min = min;
			this.max = max;
		}

		@Override
		protected IConstr[] put(Sat4jWrapper wrapper, IPBSolver solver) throws ContradictionException {
			// Sat4j doesn't seem to handle exactly correctly ATM
			// however it's a non-issue, since internally it's just both atMost and atLeast anyway.
			IVecInt clause = wrapper.mapOptionsToSat4jClause(options);
			return new IConstr[] { solver.addAtMost(clause, max), solver.addAtLeast(clause, min) };
		}

		@Override
		public String toString() {
			return "Between " + min + ", " + max + " " + Arrays.toString(options);
		}
	}
}
