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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModLoadType;
import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A concrete definition that allows the modid to be loaded from any of a set of {@link ModLoadOption}s. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class OptionalModIdDefintion extends ModIdDefinition {

	static final Comparator<ModLoadOption> MOD_COMPARATOR = (a, b) -> {

		Version va = a.version();
		Version vb = b.version();

		if (va.isSemantic() && vb.isSemantic()) {
			return va.semantic().compareTo(vb.semantic());
		}

		return va.raw().compareTo(vb.raw());
	};

	final RuleContext ctx;
	final String modid;
	final List<ModLoadOption> sources = new ArrayList<>();

	public OptionalModIdDefintion(RuleContext ctx, String modid) {
		this.ctx = ctx;
		this.modid = modid;
	}

	@Override
	public String getModId() {
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
			String opName = option.metadata().name();

			if (name == null) {
				name = opName;
			} else if (!name.equals(opName)) {
				// TODO!
			}
		}

		return modid;
	}

	@Override
	public boolean onLoadOptionAdded(LoadOption option) {
		if (option instanceof ModLoadOption) {
			ModLoadOption mod = (ModLoadOption) option;
			if (mod.id().equals(modid)) {
				sources.add(mod);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean onLoadOptionRemoved(LoadOption option) {
		return sources.remove(option);
	}

	private void recalculateWeights() {
		sources.sort(MOD_COMPARATOR);

		// IF_REQUIRED uses a positive weight to discourage it from being chosen
		// IF_POSSIBLE uses a negative weight to encourage it to be chosen
		// ALWAYS is handled directly in define()
		int index = 0;

		for (ModLoadOption mod : sources) {
			int weight;
			if (mod instanceof AliasedLoadOption) {
				weight = 0;
			} else {
				weight = 1000;

				if (mod.metadata().loadType() == ModLoadType.IF_POSSIBLE) {
					weight = -weight;
				}
			}

			// Always prefer newer (larger) versions
			// by subtracting the larger index
			weight -= index++;
			ctx.setWeight(mod, this, weight);
		}
	}

	@Override
	public void define(RuleDefiner definer) {
		if (sources.isEmpty()) {
			return;
		}

		boolean anyAreAlways = false;

		for (ModLoadOption mod : sources) {
			if (mod.metadata().loadType() == ModLoadType.ALWAYS) {
				anyAreAlways = true;
				break;
			}
		}

		recalculateWeights();

		LoadOption[] array = sources.toArray(new LoadOption[0]);

		array = definer.deduplicate(array);

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
			errors.append("\n\t - ");
			errors.append(option.getSpecificInfo());
		}
	}
}
