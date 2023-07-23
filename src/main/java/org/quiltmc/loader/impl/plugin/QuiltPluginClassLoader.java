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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.transformer.InternalsHiderTransform;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class QuiltPluginClassLoader extends ClassLoader {

	final QuiltPluginContextImpl context;
	final Path from;
	final Set<String> loadablePackages;

	public QuiltPluginClassLoader(QuiltPluginContextImpl context, ClassLoader parent, Path from,
		ModMetadataExt.ModPlugin plugin) {

		super(parent);
		this.context = context;
		this.from = from;
		this.loadablePackages = new HashSet<>(plugin.packages());
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> c = loadClassInner(name);
		if (c == null) {
			return super.loadClass(name, resolve);
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	Class<?> loadClassDirectly(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> c = loadClassInner(name);
		if (c == null) {
			throw new ClassNotFoundException(name);
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	private Class<?> loadClassInner(String name) throws ClassNotFoundException {

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
				byte[] src = FileUtil.readAllBytes(is);

				InternalsHiderTransform transform = new InternalsHiderTransform(InternalsHiderTransform.Target.PLUGIN);

				src = transform.run(context.optionFrom, src);

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

		return context.manager.findClass(name, pkg);
	}

	@Override
	protected URL findResource(String name) {
		// We don't need to support resources (yay)
		return null;
	}
}
