package org.quiltmc.loader.impl.util;

import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.LanguageAdapterException;
import org.quiltmc.loader.api.ModContainer;

import net.fabricmc.loader.launch.common.FabricLauncherBase;

public class ModLanguageAdapter implements LanguageAdapter {

	private final ModContainer from;
	private final String name, target;
	private LanguageAdapter adapter;
	private boolean loadFailed = false;

	public ModLanguageAdapter(ModContainer from, String name, String target) {
		this.from = from;
		this.name = name;
		this.target = target;
	}

	public void init() {
		if (!loadFailed && adapter == null) {
			try {
				adapter = create();
			} catch (Exception e) {
				throw new RuntimeException(errorMessage(), e);
			}
		}
	}

	private LanguageAdapter create() throws Exception {
		loadFailed = true;
		ClassLoader classloader = FabricLauncherBase.getLauncher().getTargetClassLoader();
		Class<?> adapterClass = Class.forName(target, true, classloader);
		LanguageAdapter la = (LanguageAdapter) adapterClass.getDeclaredConstructor().newInstance();
		loadFailed = false;
		return la;
	}

	private String errorMessage() {
		return "Failed to instantiate language adapter: " + name + " from " + from.metadata().id();
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		if (adapter == null) {
			if (loadFailed) {
				throw new LanguageAdapterException("Already tried to load - please check the log for details");
			}
			try {
				adapter = create();
			} catch (Exception e) {
				throw new LanguageAdapterException(errorMessage(), e);
			}
		}
		return adapter.create(mod, value, type);
	}

}
