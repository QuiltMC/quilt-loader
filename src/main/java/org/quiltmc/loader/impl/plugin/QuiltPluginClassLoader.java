package org.quiltmc.loader.impl.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.quiltmc.loader.api.plugin.ModMetadataExt;

class QuiltPluginClassLoader extends ClassLoader {

	final QuiltPluginManagerImpl manager;
	final Path from;
	final Set<String> loadablePackages;

	public QuiltPluginClassLoader(QuiltPluginManagerImpl manager, ClassLoader parent, Path from,
		ModMetadataExt.ModPlugin plugin) {

		super(parent);
		this.manager = manager;
		this.from = from;
		this.loadablePackages = new HashSet<>(plugin.packages());
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {

		String pkg = null;

		load_from_plugin: {

			int lastDot = name.lastIndexOf('.');
			if (lastDot < 0) {
				break load_from_plugin;
			}

			pkg = name.substring(0, lastDot);

			if (!loadablePackages.contains(pkg)) {
				break load_from_plugin;
			}

			String path = name.replace(".", from.getFileSystem().getSeparator()).concat(".class");

			try (InputStream is = Files.newInputStream(from.resolve(path))) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				int read;
				byte[] buffer = new byte[256];
				while ((read = is.read(buffer)) > 0) {
					baos.write(buffer, 0, read);
				}

				byte[] src = baos.toByteArray();

				try {
					definePackage(pkg, null, null, null, null, null, null, null);
				} catch (IllegalArgumentException e) {
					// Ignored
					// we do it this way since (apparently) this can be called from multiple threads at once
				}

				return defineClass(name, src, 0, src.length);

			} catch (IOException io) {
				throw new ClassNotFoundException("Unable to load the file ", io);
			}
		}

		Class<?> cls = manager.findClass(name, pkg);

		if (cls != null) {
			return cls;
		}

		return super.findClass(name);
	}

	@Override
	protected URL findResource(String name) {
		// We don't need to support resources (yay)
		return null;
	}
}
