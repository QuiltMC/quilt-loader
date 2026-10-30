/*
 * Copyright 2026 QuiltMC
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

package org.quiltmc.loader.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

final class ModDependentsDebug {
	private ModDependentsDebug() {}

	static void logModsWithoutDependents(Collection<ModLoadOption> mods) {
		if (!Boolean.getBoolean(SystemProperties.DEBUG_LOG_MODS_WITHOUT_DEPENDENTS)) {
			return;
		}

		Map<String, ModLoadOption> byId = new HashMap<>();
		Map<String, ModLoadOption> providedBy = new HashMap<>();
		for (ModLoadOption mod : mods) {
			byId.put(mod.id(), mod);
			for (ProvidedMod provided : mod.metadata().provides()) {
				providedBy.put(provided.id(), mod);
			}
		}

		Set<ModLoadOption> hasDependents = new HashSet<>();
		for (ModLoadOption mod : mods) {
			for (ModDependency dep : mod.metadata().depends()) {
				markDependents(dep, byId, providedBy, hasDependents);
			}
		}

		List<ModLoadOption> withoutDependents = new ArrayList<>();
		for (ModLoadOption mod : mods) {
			if (!hasDependents.contains(mod)) {
				withoutDependents.add(mod);
			}
		}
		withoutDependents.sort(Comparator.comparing(ModLoadOption::id));

		if (withoutDependents.isEmpty()) {
			Log.info(LogCategory.GENERAL, "All loaded mods have at least one dependent mod.");
			return;
		}

		Log.info(LogCategory.GENERAL, "Loaded mods with no dependents (%d):", withoutDependents.size());
		for (ModLoadOption mod : withoutDependents) {
			Log.info(LogCategory.GENERAL, " - %s %s (%s)", mod.id(), mod.version(), mod.metadata().name());
		}
	}

	private static void markDependents(
		ModDependency dep,
		Map<String, ModLoadOption> byId,
		Map<String, ModLoadOption> providedBy,
		Set<ModLoadOption> hasDependents
	) {
		if (dep.shouldIgnore()) {
			return;
		}

		if (dep instanceof ModDependency.Only) {
			ModLoadOption target = findTarget((ModDependency.Only) dep, byId, providedBy);
			if (target != null) {
				hasDependents.add(target);
			}
			return;
		}

		Collection<? extends ModDependency> children;
		if (dep instanceof ModDependency.Any) {
			children = (ModDependency.Any) dep;
		} else {
			children = (ModDependency.All) dep;
		}

		for (ModDependency child : children) {
			markDependents(child, byId, providedBy, hasDependents);
		}
	}

	private static ModLoadOption findTarget(
		ModDependency.Only dep,
		Map<String, ModLoadOption> byId,
		Map<String, ModLoadOption> providedBy
	) {
		ModDependencyIdentifier id = dep.id();
		ModLoadOption mod = byId.get(id.id());
		if (mod == null) {
			mod = providedBy.get(id.id());
		}
		if (mod == null) {
			return null;
		}

		String maven = id.mavenGroup();
		if (!maven.isEmpty() && !maven.equals(mod.group())) {
			return null;
		}

		if (!dep.matches(mod.version())) {
			return null;
		}

		return mod;
	}
}
