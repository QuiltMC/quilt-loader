package net.fabricmc.test;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.entrypoint.GameTransformer;
import org.quiltmc.loader.impl.launch.common.MappingConfiguration;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

public class DummyQuiltLauncher implements QuiltLauncher {

	@Override
	public MappingConfiguration getMappingConfiguration() {
		return new MappingConfiguration() {
			@Override
			public List<String> getNamespaces() {
				return Collections.singletonList("intermediary");
			}

			@Override
			public String getTargetNamespace() {
				return "intermediary";
			}
		};
	}

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {

	}

	@Override
	public void addToClassPath(Path path, ModContainer mod, URL origin, String... allowedPrefixes) {

	}

	@Override
	public void setAllowedPrefixes(Path path, String... prefixes) {

	}

	@Override
	public void setTransformCache(URL insideTransformCache) {

	}

	@Override
	public void setHiddenClasses(Set<String> classes) {

	}

	@Override
	public void setHiddenClasses(Map<String, String> classes) {

	}

	@Override
	public void setPluginPackages(Map<String, ClassLoader> hiddenClasses) {

	}

	@Override
	public void hideParentUrl(URL hidden) {

	}

	@Override
	public void hideParentPath(Path obf) {

	}

	@Override
	public void validateGameClassLoader(Object gameInstance) {

	}

	@Override
	public EnvType getEnvironmentType() {
		return null;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return false;
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		return null;
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return null;
	}

	@Override
	public URL getResourceURL(String name) {
		return null;
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return null;
	}

	@Override
	public ClassLoader getClassLoader(ModContainer mod) {
		return null;
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		return new byte[0];
	}

	@Override
	public Manifest getManifest(Path originPath) {
		return null;
	}

	@Override
	public boolean isDevelopment() {
		return false;
	}

	@Override
	public String getEntrypoint() {
		return "";
	}

	@Override
	public List<Path> getClassPath() {
		return Collections.emptyList();
	}

	@Override
	public GameTransformer getEntrypointTransformer() {
		return null;
	}
}
