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

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.entrypoint.GameEntrypoint;

import net.fabricmc.api.EnvType;

/**
 * A mod initializer ran only on {@link EnvType#CLIENT}.
 *
 * <p>This entrypoint is suitable for setting up client-specific logic, such as rendering
 * or integrated server tweaks.</p>
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code client} key.</p>
 *
 * @see ModInitializer
 * @see DedicatedServerModInitializer
 * @see org.quiltmc.loader.api.QuiltLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface ClientModInitializer extends GameEntrypoint {
	/**
	 * Runs the mod initializer on the client environment.
	 */
	void onInitializeClient(ModContainer mod);
}
