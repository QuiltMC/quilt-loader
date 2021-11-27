package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

class BuiltinPluginContext extends BasePluginContext {

	public BuiltinPluginContext(QuiltPluginManagerImpl manager) {
		super(manager);
	}

	@Override
	public Path pluginPath() {
		throw new UnsupportedOperationException("Builtin plugins don't support pluginPath()");
	}

	@Override
	public void lockZip(Path path) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public <V> Future<V> submit(Callable<V> task) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public RuleContext ruleContext() {
		return manager.solver;
	}

	@Override
	public <T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public void blameRule(Rule rule) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}
}
