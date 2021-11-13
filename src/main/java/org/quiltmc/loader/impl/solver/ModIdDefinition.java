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

import java.util.Collection;
import java.util.Collections;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.util.sat4j.pb.tools.DependencyHelper;

/** A concrete definition of a modid. This also maps the modid to the {@link LoadOption} candidates, and so is used
 * instead of {@link LoadOption} in other links. */
abstract class ModIdDefinition extends Rule {
	abstract String getModId();

	/** @return An array of all the possible {@link LoadOption} instances that can define this modid. May be empty, but
	 *         will never be null. */
	abstract ModLoadOptionCls[] sources();

	abstract String getFriendlyName();

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public boolean isNode() {
		return false;
	}

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.emptySet();
	}

	/** @deprecated Not used yet. In the future this will be used for better error message generation. */
	@Deprecated
	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return Collections.emptySet();
	}
}
