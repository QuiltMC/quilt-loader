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

package org.quiltmc.loader.impl.plugin.quilt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.solver.ModSolver;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

public class QuiltRuleDepOnly extends QuiltRuleDep {

	final ModDependency.Only publicDep;
	final List<ModLoadOption> validOptions;
	final List<ModLoadOption> invalidOptions;
	final List<ModLoadOption> allOptions;

	final QuiltRuleDep unless;

	public QuiltRuleDepOnly(RuleContext ctx, LoadOption source, ModDependency.Only publicDep) {
		super(source);
		this.publicDep = publicDep;
		validOptions = new ArrayList<>();
		invalidOptions = new ArrayList<>();
		allOptions = new ArrayList<>();

		if (ModSolver.DEBUG_PRINT_STATE) {
			Log.info(LogCategory.SOLVING, "Adding a mod dependency from " + source + " to " + publicDep.id().id());
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
	public boolean onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;

			if (!mod.id().equals(publicDep.id().id())) {
				return false;
			}

			allOptions.add(mod);

			String maven = publicDep.id().mavenGroup();
			boolean groupMatches = maven.isEmpty() || maven.equals(mod.group());

			if (groupMatches && publicDep.matches(mod.version())) {
				validOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					Log.info(LogCategory.SOLVING, "  +  valid option: " + mod.fullString());
				}
			} else {
				invalidOptions.add(mod);

				if (ModSolver.DEBUG_PRINT_STATE) {
					String reason = groupMatches ? "mismatched group" : "wrong version";
					Log.info(LogCategory.SOLVING, "  x  mismatching option: " + mod.fullString() + " because " + reason);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onLoadOptionRemoved(LoadOption option) {
		boolean changed = validOptions.remove(option);
		changed |= invalidOptions.remove(option);
		allOptions.remove(option);
		return changed;
	}

	@Override
	public void define(RuleDefiner definer) {

		boolean optional = publicDep.optional();
		List<ModLoadOption> options = optional ? invalidOptions : validOptions;

		if (optional && options.isEmpty()) {
			return;
		}

		LoadOption[] array = new LoadOption[options.size() + (unless == null ? 1 : 2)];
		int i = 0;

		for (; i < options.size(); i++) {
			array[i] = options.get(i);
			if (optional) {
				array[i] = definer.negate(array[i]);
			}
		}

		// i is incremented when we exit the for loop, so this is fine.
		array[i++] = definer.negate(source);
		if (unless != null) {
			array[i] = unless.source;
		}

		definer.atLeastOneOf(array);
	}

	@Override
	boolean hasAnyValidOptions() {
		return !validOptions.isEmpty();
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

		if (publicDep.optional()) {
			errors.append("Optional dependency for ");
		} else {
			errors.append("Dependency for ");
		}

		errors.append(source);
		errors.append(" on ");
		errors.append(publicDep.id());
		errors.append(" versions ");
		errors.append(publicDep.versions());
		errors.append(" (");
		errors.append(validOptions.size());
		errors.append(" valid options, ");
		errors.append(invalidOptions.size());
		errors.append(" invalid options)");

		for (ModLoadOption option : validOptions) {
			errors.append("\n\t+ " + option.fullString());
		}

		for (ModLoadOption option : invalidOptions) {
			errors.append("\n\tx " + option.fullString());
		}
	}
}
