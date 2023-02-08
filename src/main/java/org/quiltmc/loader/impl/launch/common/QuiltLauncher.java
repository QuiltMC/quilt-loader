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

package org.quiltmc.loader.impl.launch.common;

import net.fabricmc.api.EnvType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public interface QuiltLauncher {
	MappingConfiguration getMappingConfiguration();

	void addToClassPath(Path path, String... allowedPrefixes);
	void addToClassPath(Path path, ModContainer mod, URL origin, String... allowedPrefixes);
	void setAllowedPrefixes(Path path, String... prefixes);
	void setTransformCache(URL insideTransformCache);
	void hideParentUrl(URL hidden);
	void hideParentPath(Path obf);
	void validateGameClassLoader(Object gameInstance);

	EnvType getEnvironmentType();

	boolean isClassLoaded(String name);

	/**
	 * Load a class into the game's class loader even if its bytes are only available from the parent class loader.
	 */
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

	InputStream getResourceAsStream(String name);

	ClassLoader getTargetClassLoader();

	ClassLoader getClassLoader(ModContainer mod);

	/**
	 * Gets the byte array for a particular class.
	 *
	 * @param name The name of the class to retrieve
	 * @param runTransformers Whether to run all transformers <i>except mixin</i> on the class
	 */
	byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;

	Manifest getManifest(Path originPath);

	boolean isDevelopment();

	String getEntrypoint();

	String getTargetNamespace();

	List<Path> getClassPath();
}
