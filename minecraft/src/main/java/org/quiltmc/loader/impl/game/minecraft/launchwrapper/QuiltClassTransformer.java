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

package org.quiltmc.loader.impl.game.minecraft.launchwrapper;

import org.quiltmc.loader.impl.transformer.QuiltTransformer;
import net.minecraft.launchwrapper.IClassTransformer;

<<<<<<<< HEAD:src/main/java/org/quiltmc/loader/impl/game/minecraft/launchwrapper/QuiltClassTransformer.java
public class QuiltClassTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		return QuiltTransformer.lwTransformerHook(name, transformedName, basicClass);
========
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.transformer.FabricTransformer;

public class FabricClassTransformer implements IClassTransformer {
	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		boolean isDevelopment = FabricLauncherBase.getLauncher().isDevelopment();
		EnvType envType = FabricLauncherBase.getLauncher().getEnvironmentType();

		byte[] input = FabricLoaderImpl.INSTANCE.getGameProvider().getEntrypointTransformer().transform(name);

		if (input != null) {
			return FabricTransformer.transform(isDevelopment, envType, name, input);
		} else {
			if (bytes != null) {
				return FabricTransformer.transform(isDevelopment, envType, name, bytes);
			} else {
				return null;
			}
		}
>>>>>>>> fabric-master:minecraft/src/main/java/net/fabricmc/loader/impl/game/minecraft/launchwrapper/FabricClassTransformer.java
	}
}
