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

package org.quiltmc.loader.impl.plugin.quilt;

import java.util.concurrent.atomic.AtomicInteger;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Used to indicate part of a {@link ModDependency} from quilt.mod.json. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltModDepOption extends LoadOption {
	private static final AtomicInteger IDS = new AtomicInteger();

	public final ModDependency dep;
	private final int id = IDS.incrementAndGet();

	public QuiltModDepOption(ModDependency dep) {
		this.dep = dep;
	}

	@Override
	public String toString() {
		return dep.toString();
	}

	@Override
	public QuiltLoaderText describe() {
		return QuiltLoaderText.translate("solver.option.dep_technical", id, dep.toString());
	}
}
