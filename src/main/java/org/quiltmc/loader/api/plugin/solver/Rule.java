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

package org.quiltmc.loader.api.plugin.solver;

import java.util.Collection;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Base definition of a link between one or more {@link LoadOption}s, that */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public abstract class Rule {

	public Rule() {}

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the added option had no
	 *         affect on this rule. */
	public abstract boolean onLoadOptionAdded(LoadOption option);

	/** @return true if {@link #define(RuleDefiner)} needs to be called again, or false if the removed option had no
	 *         affect on this rule. */
	public abstract boolean onLoadOptionRemoved(LoadOption option);

	/** Called whenever a {@link LoadOption} is changed. Not all {@link Rule}s are expected to be able to update to all
	 * changes - instead this affects only minor things, like whether {@link ModDependency#shouldIgnore()} is different.
	 * 
	 * @return True if {@link #define(RuleDefiner)} needs to be called again, or false if option changing doesn't affect
	 *         this rule. */
	public boolean onLoadOptionChanged(LoadOption option) {
		return false;
	}

	public abstract void define(RuleDefiner definer);

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

	/** Used to create a graph of all rules, in order to find out the root problems. This should return all
	 * {@link LoadOption}s which 'cause' this rule to exist. (For example 'buildcraft' might declare a dependency on
	 * 'minecraft', and that dependency rule would return 'buildcraft' from this). */
	public abstract Collection<? extends LoadOption> getNodesFrom();

	/** Used to create a graph of all rules, in order to find out the root problems. This should return all
	 * {@link LoadOption}s which are modified by this rule (but don't cause it). (For example 'buildcraft' might declare
	 * a dependency on 'minecraft', and that dependency rule would return 'minecraft' from this). */
	public abstract Collection<? extends LoadOption> getNodesTo();

	public abstract void fallbackErrorDescription(StringBuilder errors);
}
