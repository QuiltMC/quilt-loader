/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.discovery;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Thrown when an exception occurs while solving the set of mods, which is caused by those mods - in other words the
 * user is expected to be able to fix this error by adding or removing mods, or by asking a mod author to fix their
 * quilt.mod.json file. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class ModSolvingException extends ModResolutionException {

	public ModSolvingException(String s) {
		super(s);
	}

	public ModSolvingException(Throwable t) {
		super(t);
	}

	public ModSolvingException(String s, Throwable t) {
		super(s, t);
	}
}
