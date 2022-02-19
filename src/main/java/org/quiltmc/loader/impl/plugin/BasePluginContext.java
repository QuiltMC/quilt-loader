package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

abstract class BasePluginContext implements QuiltPluginContext {

	final QuiltPluginManagerImpl manager;
	final String pluginId;
	final Set<Path> modFolderSet = new ModFolderSet();
	final RuleContext ruleContext = new ModRuleContext();

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
		manager.scanModFile(file, guiNode);
	}

	@Override
	public void lockZip(Path path) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
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
	public <T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option) {
		addTentativeOption0(option);
	}

	private void addTentativeOption0(LoadOption option) {
		manager.addLoadOption(option, this);
	}

	@Override
	public void blameRule(Rule rule) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	class ModFolderSet implements Set<Path> {

		@Override
		public boolean isEmpty() {
			return manager.modFolders.isEmpty();
		}

		@Override
		public int size() {
			return manager.modFolders.size();
		}

		@Override
		public boolean contains(Object o) {
			return manager.modFolders.containsKey(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return manager.modFolders.keySet().containsAll(c);
		}

		@Override
		public Iterator<Path> iterator() {
			return Arrays.asList(toArray(new Path[0])).iterator();
		}

		@Override
		public Object[] toArray() {
			return manager.modFolders.keySet().toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return manager.modFolders.keySet().toArray(a);
		}

		@Override
		public boolean add(Path e) {
			return manager.addModFolder(e, BasePluginContext.this);
		}

		@Override
		public boolean addAll(Collection<? extends Path> c) {
			boolean changed = false;
			for (Path p : c) {
				changed |= add(p);
			}
			return changed;
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	class ModRuleContext implements RuleContext {

		@Override
		public void addOption(LoadOption option) {
			if (option instanceof TentativeLoadOption) {
				addTentativeOption0(option);
			} else if (option instanceof ModLoadOption) {
				ModLoadOption mod = (ModLoadOption) option;
				Path from = mod.from();
				manager.addSingleModOption(from, mod, BasePluginContext.this, null, guiNode);
			} else {
				manager.addLoadOption(option, BasePluginContext.this);
			}
		}

		@Override
		public void addOption(LoadOption option, int weight) {
			addOption(option);
			setWeight(option, weight);
		}

		@Override
		public void setWeight(LoadOption option, int weight) {
			manager.solver.setWeight(option, weight);
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

		@Override
		public boolean isNegated(LoadOption option) {
			return manager.solver.isNegated(option);
		}

		@Override
		public LoadOption negate(LoadOption option) {
			return manager.solver.negate(option);
		}
	}
}
