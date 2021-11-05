/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.api.minecraft;

import org.quiltmc.loader.impl.QuiltLoaderImpl;

import net.fabricmc.api.EnvType;

/** Public access for some minecraft-specific functionality in quilt loader. */
public final class MinecraftQuiltLoader {
	private MinecraftQuiltLoader() {}

	/**
	 * Get the current environment type.
	 *
	 * @return the current environment type
	 */
	public static EnvType getEnvironmentType() {
		// TODO: Get this from a plugin instead!
		QuiltLoaderImpl impl = QuiltLoaderImpl.INSTANCE;
		if (impl == null) {
			throw new IllegalStateException("Accessed QuiltLoader too early!");
		}
		return impl.getEnvironmentType();
	}
}
