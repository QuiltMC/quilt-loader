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

package org.quiltmc.loader.impl.launch.knot;

import java.util.Collection;
import java.util.Collections;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;

public class MixinContainerHandleMod implements IContainerHandle {
	@Override
	public String getAttribute(String name) {
		return null;
	}

	@Override
	public Collection<IContainerHandle> getNestedContainers() {
		return Collections.emptyList();
	}
}
