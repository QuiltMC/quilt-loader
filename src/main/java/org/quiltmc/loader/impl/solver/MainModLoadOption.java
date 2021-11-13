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

package org.quiltmc.loader.impl.solver;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.impl.discovery.ModCandidateCls;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

class MainModLoadOption extends ModLoadOptionCls {
	/** Used to identify this {@link MainModLoadOption} against others with the same modid. A value of -1 indicates that
	 * this is the only {@link LoadOption} for the given modid. */
	final int index;

	MainModLoadOption(ModCandidateCls candidate, int index) {
		super(candidate);
		this.index = index;
	}

	@Override
	String shortString() {
		if (index == -1) {
			return "mod '" + id() + "'";
		} else {
			return "mod '" + id() + "'#" + (index + 1);
		}
	}

	@Override
	String getSpecificInfo() {
		LoaderModMetadata info = candidate.getInfo();
		return "version " + info.getVersion() + " loaded from " + getLoadSource();
	}
}
