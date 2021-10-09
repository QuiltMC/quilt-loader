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
