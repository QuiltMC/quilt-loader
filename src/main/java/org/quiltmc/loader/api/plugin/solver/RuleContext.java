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

package org.quiltmc.loader.api.plugin.solver;

import org.quiltmc.loader.impl.solver.Sat4jWrapper;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface RuleContext {

	/** Adds a new {@link LoadOption}, without any weight. */
	void addOption(LoadOption option);

	/** Sets the weight of an existing {@link LoadOption}, applied by the given rule.
	 * 
	 * @param key The rule which provides the weight modifier. May be null. If not null then this weight modifier will
	 *            be removed whenever the rule is removed.
	 * @param weight The weight. Negative values will mean the solver will try to include the option, positive values
	 *            encourage the solver to pick a different option. Absolute values are unimportant when only 1 of a set
	 *            of options can be chosen, instead only relative will be important. The sum of all weights is used when
	 *            solving.
	 * @throws IllegalArgumentException if the given {@link LoadOption} isn't present. */
	void setWeight(LoadOption option, Rule key, int weight);

	void removeOption(LoadOption option);

	/** Adds a new {@link Rule} to this solver. This calls {@link Rule#onLoadOptionAdded(LoadOption)} for every
	 * {@link LoadOption} currently held, and calls {@link Rule#define(RuleDefiner)} once afterwards. */
	void addRule(Rule rule);

	/** Clears any current definitions this rule is associated with, and calls {@link Rule#define(RuleDefiner)} */
	void redefine(Rule rule);

	public static boolean isNegated(LoadOption option) {
		return Sat4jWrapper.isNegated(option);
	}

	public static LoadOption negate(LoadOption option) {
		return Sat4jWrapper.negate(option);
	}
}
