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

package org.quiltmc.loader.impl.plugin.quilt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltRuleDepAny extends QuiltRuleDep {

	final QuiltRuleDepOnly[] options;
	final ModDependency.Any publicDep;

	public QuiltRuleDepAny(QuiltPluginManager manager, RuleContext ctx, LoadOption option, ModDependency.Any any) {

		super(option);
		this.publicDep = any;
		List<QuiltRuleDepOnly> optionList = new ArrayList<>();

		for (ModDependency.Only only : any) {
			if (!only.shouldIgnore()) {
				QuiltModDepOption sub = new QuiltModDepOption(only);
				ctx.addOption(sub);
				QuiltRuleDepOnly dep = new QuiltRuleDepOnly(manager, ctx, sub, only);
				ctx.addRule(dep);
				optionList.add(dep);
			}
		}

		this.options = optionList.toArray(new QuiltRuleDepOnly[0]);
	}

	@Override
	public boolean onLoadOptionAdded(LoadOption option) {
		return false;
	}

	@Override
	public boolean onLoadOptionRemoved(LoadOption option) {
		return false;
	}

	@Override
	public void define(RuleDefiner definer) {
		LoadOption[] array = new LoadOption[options.length + 1];
		int i = 0;

		for (; i < options.length; i++) {
			array[i] = options[i].source;
		}
		array[i] = definer.negate(source);
		definer.atLeastOneOf(array);
	}

	@Override
	boolean hasAnyValidOptions() {
		for (QuiltRuleDepOnly on : options) {
			if (on.hasAnyValidOptions()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		// FIXME: This needs a proper toString()
		return "" + publicDep;
	}

	@Override
	public Collection<? extends LoadOption> getNodesFrom() {
		return Collections.singleton(source);
	}

	@Override
	public Collection<? extends LoadOption> getNodesTo() {
		List<LoadOption> list = new ArrayList<>();
		for (QuiltRuleDepOnly on : options) {
			list.add(on.source);
		}
		return list;
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Dependancy for ");
		errors.append(source);
		errors.append(" on any of: ");

		for (QuiltRuleDepOnly on : options) {
			errors.append("\n\t-");
			errors.append(on.source);
			errors.append(" ");
		}
	}

	@Override
	public void appendRuleDescription(Consumer<QuiltLoaderText> to) {
		to.accept(QuiltLoaderText.translate("solver.rule.dep.any", source.describe()));
		for (QuiltRuleDepOnly on : options) {
			to.accept(on.source.describe());
		}
	}
}
