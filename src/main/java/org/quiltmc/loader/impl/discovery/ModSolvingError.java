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

package org.quiltmc.loader.impl.discovery;

/** Thrown when something goes wrong internally during solving, rather than being the fault of mod files. In other words
 * it's caused by a bug in quilt loader, or one of it's plugins. */
public class ModSolvingError extends ModResolutionException {
	public ModSolvingError(String s) {
		super(s);
	}

	public ModSolvingError(Throwable t) {
		super(t);
	}

	public ModSolvingError(String s, Throwable t) {
		super(s, t);
	}
}
