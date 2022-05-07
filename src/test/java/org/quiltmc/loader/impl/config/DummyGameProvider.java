package org.quiltmc.loader.impl.config;

import net.fabricmc.loader.launch.common.FabricLauncher;

import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.Arguments;

import java.nio.file.Path;
import java.util.Collection;

public class DummyGameProvider implements GameProvider {
	private final Path launchDir;

	public DummyGameProvider(Path launchDir) {
		this.launchDir = launchDir;
	}

	@Override
	public String getGameId() {
		return null;
	}

	@Override
	public String getGameName() {
		return null;
	}

	@Override
	public String getRawGameVersion() {
		return null;
	}

	@Override
	public String getNormalizedGameVersion() {
		return null;
	}

	@Override
	public Collection<BuiltinMod> getBuiltinMods() {
		return null;
	}

	@Override
	public String getEntrypoint() {
		return null;
	}

	@Override
	public Path getLaunchDirectory() {
		return this.launchDir;
	}

	@Override
	public boolean isObfuscated() {
		return false;
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return false;
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public boolean locateGame(FabricLauncher launcher, String[] args) {
		return false;
	}

	@Override
	public void initialize(FabricLauncher launcher) {

	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return null;
	}

	@Override
	public void unlockClassPath(FabricLauncher launcher) {

	}

	@Override
	public void launch(ClassLoader loader) {

	}

	@Override
	public Arguments getArguments() {
		return null;
	}

	@Override
	public String[] getLaunchArguments(boolean sanitize) {
		return new String[0];
	}
}
