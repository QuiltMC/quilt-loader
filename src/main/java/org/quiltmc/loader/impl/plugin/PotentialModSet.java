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
