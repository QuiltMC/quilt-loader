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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

class QuiltRuleBreakOnly extends QuiltRuleBreak {
	final ModDependency.Only publicDep;
	final List<ModLoadOption> conflictingOptions;
	final List<ModLoadOption> okayOptions;
	final List<ModLoadOption> allOptions;

	final QuiltRuleDep unless;

	public QuiltRuleBreakOnly(RuleContext ctx, LoadOption source, ModDependency.Only publicDep) {
		super(source);
		this.publicDep = publicDep;
		conflictingOptions = new ArrayList<>();
		okayOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			Log.info(LogCategory.SOLVING, "Adding a mod break from " + source + " to " + publicDep.id().id());
		}

		ModDependency except = publicDep.unless();
		if (except != null && !except.shouldIgnore()) {
			QuiltModDepOption option = new QuiltModDepOption(except);
			ctx.addOption(option);
			this.unless = ModSolver.createModDepLink(ctx, option, except);
			ctx.addRule(unless);
		} else {
			this.unless = null;
		}
	}

	@Override
	boolean onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;

			if (!mod.modId().equals(publicDep.id().id())) {
				return false;
			}

			allOptions.add(mod);

			String maven = publicDep.id().mavenGroup();
			boolean groupMatches = maven.isEmpty() || maven.equals(mod.group());

			if (groupMatches && publicDep.matches(mod.version())) {
				conflictingOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					Log.info(LogCategory.SOLVING, "  x  conflicting option: " + mod.fullString());
				}
				return true;
			} else {
				okayOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					String reason = groupMatches ? "different group" : "different version";
					Log.info(LogCategory.SOLVING, "  +  okay option: " + mod.fullString() + " because " + reason);
				}
			}

		}
		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		boolean changed = conflictingOptions.remove(option);
		changed |= okayOptions.remove(option);
		allOptions.remove(option);
		return changed;
	}

	@Override
	void define(RuleDefiner definer) {

		// "optional" is meaningless for breaks
		List<ModLoadOption> conficts = conflictingOptions;

		if (conficts.isEmpty()) {
			return;
		}

		LoadOption[] options = new LoadOption[unless == null ? 2 : 3];
		options[1] = definer.negate(source);

		if (unless != null) {
			options[2] = definer.negate(unless.source);
		}

		for (ModLoadOption conflict : conficts) {
			options[0] = definer.negate(conflict);
			definer.atLeastOneOf(options);
		}
	}

	@Override
	boolean hasAnyConflictingOptions() {
		return !conflictingOptions.isEmpty();
	}

	@Override
	public String toString() {
		return publicDep.toString();
	}

	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.singleton(source);
	}

	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		return allOptions;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {

		errors.append("Breakage for ");

		errors.append(source);
		errors.append(" on ");
		errors.append(publicDep.id());
		errors.append(" versions ");
		errors.append(publicDep.versions());
		errors.append(" (");
		errors.append(conflictingOptions.size());
		errors.append(" breaking options, ");
		errors.append(okayOptions.size());
		errors.append(" okay options)");

		for (ModLoadOption option : conflictingOptions) {
			errors.append("\n\tx " + option.fullString());
		}

		for (ModLoadOption option : okayOptions) {
			errors.append("\n\t+ " + option.fullString());
		}
	}
}
