/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.UrlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
class KnotCompatibilityClassLoader extends URLClassLoader implements KnotClassLoaderInterface {
	private final KnotClassDelegate delegate;

	KnotCompatibilityClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
		super(new URL[0], KnotCompatibilityClassLoader.class.getClassLoader());
		this.delegate = new KnotClassDelegate(isDevelopment, envType, this, provider);
	}

	@Override
	public KnotClassDelegate getDelegate() {
		return delegate;
	}

	@Override
	public boolean isClassLoaded(String name) {
		synchronized (getClassLoadingLock(name)) {
			return findLoadedClass(name) != null;
		}
	}

	@Override
	public Class<?> findLoadedClassFwd(String name) {
		return findLoadedClass(name);
	}

	@Override
	public void resolveClassFwd(Class<?> c) {
		resolveClass(c);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			return delegate.loadClass(name, getParent(), resolve);
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return delegate.tryLoadClass(name, false);
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);

			if (c == null) {
				c = delegate.tryLoadClass(name, true);

				if (c == null) {
					throw new ClassNotFoundException("can't find class "+name);
				}
			}

			resolveClass(c);

			return c;
		}
	}

	@Override
	public void addURL(URL url) {
		super.addURL(url);
	}

	@Override
	public void addPath(Path root, ModContainer mod, URL origin) {
		// TODO: Implement this properly!
		// (Or at least look into how possible that would be)
		try {
			URL asUrl = UrlUtil.asUrl(root);
			delegate.setMod(root, asUrl, mod);
			addURL(asUrl);
		} catch (MalformedURLException e) {
			throw new Error(e);
		}
	}

	@Override
	public URL getResource(String name, boolean allowFromParent) {
		if (allowFromParent) {
			return super.getResource(name);
		} else {
			return findResource(name);
		}
	}

	@Override
	public InputStream getResourceAsStream(String classFile, boolean allowFromParent) throws IOException {
		if (!allowFromParent) {
			if (findResource(classFile) == null) {
				return null;
			}
		}

		return super.getResourceAsStream(classFile);
	}

	@Override
	public Package getPackage(String name) {
		return super.getPackage(name);
	}

	@Override
	public Package definePackage(String name, String specTitle, String specVersion, String specVendor,
			String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
	}

	@Override
	public Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs) {
		return super.defineClass(name, b, off, len, cs);
	}

	@Override
	public String toString() {
		return "KnotCompatibilityClassLoader";
	}

	static {
		registerAsParallelCapable();
	}
}
