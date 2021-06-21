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

package net.fabricmc.test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.discovery.ModCandidateSet;
import org.quiltmc.loader.impl.discovery.ModResolver;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.metadata.ModMetadataParser;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;
import org.quiltmc.loader.impl.solver.ModSolveResult;
import org.quiltmc.loader.impl.solver.ModSolver;

final class ModResolvingTests {
	private static final Logger LOGGER = LogManager.getLogger();

	private static Path testLocation;

	@BeforeAll
	private static void setupPaths() {
		testLocation = new File(System.getProperty("user.dir")).toPath()//
			.resolve("src")//
			.resolve("test")//
			.resolve("resources")//
			.resolve("testing")//
			.resolve("resolving");
	}

	@Test
	public void single() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "single");

		assertModPresent(modSet, "mod-resolving-tests-single", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void duel() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "duel");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void depends() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void providedDepends() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "provided_depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library-but-renamed", "1.0.0");
		assertProvidedPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void includedDep() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "included_dep");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void altDep() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "alt_deps");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "2.0.0");
		assertNoMoreMods(modSet);
	}

	private static ModSolveResult resolveModSet(String type, String subpath) throws Exception {

		Map<String, ModCandidateSet> candidateMap = new HashMap<>();

		Path modRoot = testLocation.resolve(type).resolve(subpath);

		List<Path> subFolders = Files.list(modRoot)//
			.filter(p -> p.getFileName().toString().endsWith(".jar") && Files.isDirectory(p))//
			.collect(Collectors.toCollection(ArrayList::new));

		List<Path> loadFrom = new ArrayList<>();
		int depth = 0;

		loadFrom.addAll(subFolders);

		while (!loadFrom.isEmpty()) {
			subFolders.clear();

			for (Path modPath : loadFrom) {

				URL url = modPath.toUri().toURL();
				LoaderModMetadata[] metas = { ModMetadataParser.parseMetadata(LOGGER, modPath.resolve("fabric.mod.json")) };

				for (LoaderModMetadata meta : metas) {
					ModCandidate candidate = new ModCandidate(meta, url, depth, false);
					candidateMap.computeIfAbsent(candidate.getInfo().getId(), ModCandidateSet::new).add(candidate);

					for (NestedJarEntry jar : meta.getJars()) {
						Path sub = modPath;

						for (String part : jar.getFile().split("/")) {
							sub = sub.resolve(part);
						}

						subFolders.add(sub);
					}
				}
			}

			loadFrom.clear();
			loadFrom.addAll(subFolders);
			depth++;
		}

		return new ModSolver(LOGGER).findCompatibleSet(candidateMap);
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertModPresent(ModSolveResult result, String modid, String version) {
		ModCandidate mod = result.modMap.remove(modid);

		if (mod == null) {
			Assertions.fail(modid + " is missing from " + result.modMap);
		} else {
			Assertions.assertEquals(version, mod.getInfo().getVersion().getFriendlyString());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertModMissing(ModSolveResult result, String modid) {
		ModCandidate mod = result.modMap.remove(modid);

		if (mod != null) {
			Assertions.fail(modid + " is not missing, and instead is loaded: " + mod);
		}
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertProvidedPresent(ModSolveResult result, String modid, String version) {
		ModCandidate mod = result.providedMap.remove(modid);

		if (mod == null) {
			Assertions.fail(modid + " is missing from " + result.providedMap);
		} else {
			Assertions.assertEquals(version, mod.getInfo().getVersion().getFriendlyString());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertProvidedMissing(ModSolveResult result, String modid) {
		ModCandidate mod = result.providedMap.remove(modid);

		if (mod != null) {
			Assertions.fail(modid + " is not missing, and instead is provided by: " + mod);
		}
	}

	private static void assertNoMoreMods(ModSolveResult result) {
		if (!result.modMap.isEmpty()) {
			Assertions.fail("Expected to find no more mods loaded, but found: " + result.modMap);
		}
		if (!result.providedMap.isEmpty()) {
			Assertions.fail("Expected to find no more provided mods loaded, but found: " + result.modMap);
		}
	}
}
