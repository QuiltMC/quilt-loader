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

import java.util.ArrayList;
import java.util.List;

import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.metadata.qmj.ModLoadType;

/** A concrete definition that allows the modid to be loaded from any of a set of {@link ModCandidate}s. */
final class OptionalModIdDefintion extends ModIdDefinition {
	final String modid;
	final List<ModLoadOption> sources = new ArrayList<>();

	public OptionalModIdDefintion(String modid) {
		this.modid = modid;
	}

	@Override
	String getModId() {
		return modid;
	}

	@Override
	ModLoadOption[] sources() {
		return sources.toArray(new ModLoadOption[0]);
	}

	@Override
	String getFriendlyName() {
		String name = null;

		for (ModLoadOption option : sources) {
			String opName = option.candidate.getMetadata().name();

			if (name == null) {
				name = opName;
			} else if (!name.equals(opName)) {
				// TODO!
			}
		}

		return modid;
	}

	@Override
	boolean onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;
			if (mod.modId().equals(modid)) {
				sources.add(mod);
				return true;
			}
		}

		return false;
	}

	@Override
	boolean onLoadOptionRemoved(LoadOption option) {
		return sources.remove(option);
	}

	@Override
	void define(RuleDefiner definer) {
		boolean anyAreAlways = false;

		for (ModLoadOption mod : sources) {
			if (mod.candidate.getMetadata().loadType() == ModLoadType.ALWAYS) {
				anyAreAlways = true;
				break;
			}
		}

		ModLoadOption[] array = sources.toArray(new ModLoadOption[0]);

		if (anyAreAlways) {
			definer.exactly(1, array);
		} else {
			definer.atMost(1, array);
		}
	}

	@Override
	public String toString() {
		switch (sources.size()) {
			case 0:
				return "unknown mod '" + modid + "'";
			case 1:
				return "optional mod '" + modid + "' (1 source)";
			default:
				return "optional mod '" + modid + "' (" + sources.size() + " sources)";
		}
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append(toString());
		for (ModLoadOption option : sources) {
			errors.append("\n\t - v");
			errors.append(option.getSpecificInfo());
		}
	}
}
