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

package org.quiltmc.loader.impl.game;

import org.quiltmc.loader.impl.launch.GameProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class GameProviders {
	private GameProviders() { }

	private static final ServiceLoader<GameProvider> PROVIDERS = ServiceLoader.load(GameProvider.class);

	public static List<GameProvider> create() {
		PROVIDERS.reload();
		List<GameProvider> providers = new ArrayList<>();
		for (GameProvider provider : PROVIDERS) {
			providers.add(provider);
		}

		return providers;
	}
}
