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

package org.quiltmc.loader.impl.plugin;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
final class PotentialModSet {

	static final Comparator<Version> VERSION_COMPARATOR = (a, b) -> {
		if (a == null) {
			return b == null ? 0 : -1;
		}
		if (b == null) {
			return 1;
		}

		if (a.isSemantic()) {
			if (b.isSemantic()) {
				return a.semantic().compareTo(b.semantic());
			} else {
				return 1;
			}
		}

		if (b.isSemantic()) {
			return -1;
		}

		return a.raw().compareTo(b.raw());
	};

	final NavigableMap<Version, List<ModLoadOption>> byVersionAll = new TreeMap<>(VERSION_COMPARATOR);
	final NavigableMap<Version, ModLoadOption> byVersionSingles = new TreeMap<>(VERSION_COMPARATOR);
	final Set<ModLoadOption> extras = new HashSet<>();
	final Set<ModLoadOption> all = new HashSet<>();
}
