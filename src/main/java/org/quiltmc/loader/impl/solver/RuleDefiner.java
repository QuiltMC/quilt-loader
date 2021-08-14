package org.quiltmc.loader.impl.solver;

/** Plugin interface to allow {@link ModLink}s to define themselves. If multiple definition methods are called then all
 * of those definitions must be matched. */
interface RuleDefiner {

	/** @return A {@link LoadOption} which is negated - that is instead of being equal to TRUE, it will be equal to
	 *         FALSE. (If you double-negate a {@link LoadOption} then it will return itself, rather than being
	 *         double-wrapped). */
	LoadOption negate(LoadOption option);

	/** Defines the current {@link ModLink} as one where at least one of the given options must be TRUE in order to
	 * match.
	 * <p>
	 * This isn't quite the same as calling {@link #atLeast(int, LoadOption...)} with a count of 1, since sat4j has a
	 * (potentially faster) implementation for standard clauses.
	 * 
	 * @param options The array of options. This is copied internally.
	 * @throws IllegalArgumentException if the options array is empty. */
	void atLeastOneOf(LoadOption... options);

	/** Defines the current {@link ModLink} as one where at least the given number of options must be TRUE in order to
	 * match.
	 * 
	 * @param options The array of options. This is copied internally.
	 * @throws IllegalArgumentException if the length of the options array is less than the required count. */
	void atLeast(int count, LoadOption... options);

	/** Defines the current {@link ModLink} as one where at most the given number of options must be TRUE in order to
	 * match.
	 * 
	 * @param options The array of options. This is copied internally.
	 * @throws IllegalArgumentException if the count is negative. */
	void atMost(int count, LoadOption... options);

	/** Defines the current {@link ModLink} as one where exactly the given number of options must be TRUE in order to
	 * match.
	 * <p>
	 * This is identical to calling {@link #atLeast(int, LoadOption...)} with the count, and
	 * {@link #atMost(int, LoadOption...)} with the count. (However debugging will show this as a sigle rule, rather
	 * than two).
	 * 
	 * @param options The array of options. This is copied internally.
	 * @throws IllegalArgumentException if the length of the options array is less than the given count. */
	void exactly(int count, LoadOption... options);

	/** Defines the current {@link ModLink} as one where between the minimum and maximum (both inclusive) of the given
	 * number of options must be TRUE in order to match.
	 * <p>
	 * This is identical to calling {@link #atLeast(int, LoadOption...)} with the minimum, and
	 * {@link #atMost(int, LoadOption...)} with the maximum. (However debugging will show this as a sigle rule, rather
	 * than two).
	 * 
	 * @param options The array of options. This is copied internally.
	 * @throws IllegalArgumentException if the length of the options array is less than the given minimum, or if the
	 *             maximum is less than the minimum. */
	void between(int min, int max, LoadOption... options);
}
