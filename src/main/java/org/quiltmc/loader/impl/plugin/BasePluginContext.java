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

package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
abstract class BasePluginContext implements QuiltPluginContext {

	final QuiltPluginManagerImpl manager;
	final String pluginId;
	final RuleContext ruleContext = new ModRuleContext();

	PluginGuiTreeNode extraModsRoot;
	Collection<Rule> blameableRules = null;
	Rule blamedRule = null;

	public BasePluginContext(QuiltPluginManagerImpl manager, String pluginId) {
		this.manager = manager;
		this.pluginId = pluginId;
	}

	@Override
	public QuiltPluginManager manager() {
		return manager;
	}

	@Override
	public String pluginId() {
		return pluginId;
	}

	@Override
	public String toString() {
		return "CTX:" + pluginId;
	}

	@Override
	public void addFileToScan(Path file, PluginGuiTreeNode guiNode) {
		// TODO: Log / store / do something to store the plugin
		manager.scanModFile(file, new ModLocationImpl(false, false), guiNode);
	}

	@Override
	public boolean addFolderToScan(Path folder) {
		return manager.addModFolder(folder, this);
	}

	@Override
	public void lockZip(Path path) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public QuiltPluginError reportError(QuiltLoaderText title) {
		return manager.reportError(this, title);
	}

	@Override
	public <V> QuiltPluginTask<V> submit(Callable<V> task) {
		return manager.submit(this, task);
	}

	@Override
	public <V> QuiltPluginTask<V> submitAfter(Callable<V> task, QuiltPluginTask<?>... deps) {
		return manager.submitAfter(this, task, deps);
	}

	@Override
	public RuleContext ruleContext() {
		return ruleContext;
	}

	@Override
	public void addModLoadOption(ModLoadOption mod, PluginGuiTreeNode guiNode) {
		manager.addSingleModOption(mod, BasePluginContext.this, true, guiNode);
	}

	@Override
	public <T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option) {
		addTentativeOption0(option);
	}

	private void addTentativeOption0(LoadOption option) {
		manager.addLoadOption(option, this);
	}

	@Override
	public void blameRule(Rule rule) {
		if (blameableRules == null) {
			throw new IllegalStateException(
				"Cannot call 'blameRule' unless we are in the middle of recovering from a solver failure!"
			);
		}

		if (!blameableRules.contains(rule)) {
			throw new IllegalArgumentException("Cannot blame a rule that isn't part of the current problem!");
		}

		if (blamedRule != null) {
			throw new IllegalStateException("Cannot blame more than 1 rule!");
		}

		blamedRule = rule;
	}

	class ModRuleContext implements RuleContext {

		@Override
		public void addOption(LoadOption option) {
			if (option instanceof TentativeLoadOption) {
				addTentativeOption0(option);
			} else if (option instanceof ModLoadOption) {
				ModLoadOption mod = (ModLoadOption) option;
				if (extraModsRoot == null) {
					extraModsRoot = manager.getModsFromPluginsGuiNode().addChild(QuiltLoaderText.translate("gui.text.plugin", pluginId),
							SortOrder.ALPHABETICAL_ORDER
					);
				}

				PluginGuiTreeNode guiNode = extraModsRoot.addChild(QuiltLoaderText.of(mod.id()));
				manager.addSingleModOption(mod, BasePluginContext.this, true, guiNode);
			} else {
				manager.addLoadOption(option, BasePluginContext.this);
			}
		}

		@Override
		public void setWeight(LoadOption option, Rule key, int weight) {
			manager.solver.setWeight(option, key, weight);
		}

		@Override
		public void removeOption(LoadOption option) {
			manager.removeLoadOption(option);
		}

		@Override
		public void addRule(Rule rule) {
			manager.addRule(rule);
		}

		@Override
		public void redefine(Rule rule) {
			manager.solver.redefine(rule);
		}
	}
}
