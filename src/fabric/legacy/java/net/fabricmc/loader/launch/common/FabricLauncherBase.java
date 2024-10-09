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

package net.fabricmc.loader.launch.common;


import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.UrlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public class FabricLauncherBase implements FabricLauncher {
	private final QuiltLauncher parent = QuiltLauncherBase.getLauncher();

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	public static FabricLauncher getLauncher() {
		return new FabricLauncherBase();
	}

	@Override
	public void propose(URL url) {
		parent.addToClassPath(UrlUtil.asPath(url));
	}

	@Override
	public EnvType getEnvironmentType() {
		return FabricLoader.getInstance().getEnvironmentType();
	}

	@Override
	public boolean isClassLoaded(String name) {
		return parent.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return parent.getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return parent.getTargetClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		return parent.getClassByteArray(name, runTransformers);
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		List<URL> ret = new ArrayList<>();

		for (Path path : parent.getClassPath()) {
			try {
				ret.add(UrlUtil.asUrl(path));
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}

		return ret;
	}
}
