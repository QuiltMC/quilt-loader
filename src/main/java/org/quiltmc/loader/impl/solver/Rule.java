/*
 * Copyright 2022 QuiltMC
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

import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;

/** Base definition of a link between one or more {@link LoadOption}s, that */
abstract class Rule {

	public Rule() {}

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the added option had no
	 *         affect on this rule. */
	abstract boolean onLoadOptionAdded(LoadOption option);

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the removed option had no
	 *         affect on this rule. */
	abstract boolean onLoadOptionRemoved(LoadOption option);

	abstract void define(RuleDefiner definer);

	/** @return A description of the link. */
	@Override
	public abstract String toString();

	/** Checks to see if this link is [...]
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public boolean isNode() {
		return true;
	}

	/** TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesFrom();

	/** TODO: Better name!
	 * 
	 * @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	public abstract Collection<? extends LoadOption> getNodesTo();

	public abstract void fallbackErrorDescription(StringBuilder errors);
}
