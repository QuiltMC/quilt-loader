package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

public class QuiltPluginContextImpl extends BasePluginContext {

	final Path pluginPath;
	final QuiltPluginClassLoader classLoader;
	final QuiltLoaderPlugin plugin;

	public QuiltPluginContextImpl(QuiltPluginManagerImpl manager, Path pluginPath, FullModMetadata.ModPlugin metadata,
		Map<String, LoaderValue> previousData) throws ReflectiveOperationException {

		super(manager);
		this.pluginPath = pluginPath;

		ClassLoader parent = getClass().getClassLoader();
		classLoader = new QuiltPluginClassLoader(manager, parent, pluginPath, metadata);

		Class<?> cls = classLoader.loadClass(metadata.pluginClass());
		Object obj = cls.getDeclaredConstructor().newInstance();
		this.plugin = (QuiltLoaderPlugin) obj;

		plugin.load(this, previousData);
	}

	@Override
	public Path pluginPath() {
		return pluginPath;
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
		// NOTE: In the future we might add info when adding rules, so we don't have a "getSolver" method in
		// QuiltPluginManager
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

	Map<String, LoaderValue> unload() {
		Map<String, LoaderValue> data = new HashMap<>();

		plugin.unload(data);

		// Just to ensure the resulting map is not empty
		data.put("quilt.plugin.reloaded", LoaderValueFactory.getFactory().bool(true));

		return data;
	}
}
