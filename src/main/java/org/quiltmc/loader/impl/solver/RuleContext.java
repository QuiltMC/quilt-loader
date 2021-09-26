package org.quiltmc.loader.impl.solver;

public interface RuleContext {

	/** Adds a new {@link LoadOption}, without any weight. */
	void addOption(LoadOption option);

	/** Adds a new {@link LoadOption}, with the given weight. */
	void addOption(LoadOption option, int weight);

	void setWeight(LoadOption option, int weight);

	void removeOption(LoadOption option);

	/** Adds a new {@link Rule} to this solver. This calls {@link Rule#onLoadOptionAdded(LoadOption)} for every
	 * {@link LoadOption} currently held, and calls {@link Rule#define(RuleDefiner)} once afterwards. */
	void addRule(Rule rule);

	/** Clears any current definitions this rule is associated with, and calls {@link Rule#define(RuleDefiner)} */
	void redefine(Rule rule);
}
