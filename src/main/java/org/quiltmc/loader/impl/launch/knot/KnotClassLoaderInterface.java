/*
 * Copyright 2022 QuiltMC
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
interface KnotClassLoaderInterface {
	KnotClassDelegate getDelegate();
	boolean isClassLoaded(String name);
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;
	void addURL(URL url);
	void addPath(Path root, ModContainer mod, URL origin);
	URL getResource(String name);
	InputStream getResourceAsStream(String filename, boolean skipOriginalLoader) throws IOException;

	Package getPackage(String name);
	Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException;
	Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs);
	void resolveClassFwd(Class<?> c);
	Class<?> findLoadedClassFwd(String name);
}
