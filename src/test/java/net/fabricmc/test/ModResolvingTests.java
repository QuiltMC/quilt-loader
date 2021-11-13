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
import java.nio.file.Path;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.impl.discovery.DirectoryModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ModCandidateCls;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModResolver;
import org.quiltmc.loader.impl.discovery.ModSolvingException;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;

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
		ModSolveResultImpl modSet = resolveModSet("valid", "single");

		assertModPresent(modSet, "mod-resolving-tests-single", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void duel() throws Exception {
		ModSolveResultImpl modSet = resolveModSet("valid", "duel");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void depends() throws Exception {
		ModSolveResultImpl modSet = resolveModSet("valid", "depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void mavenGroups() throws Exception {
		ModSolveResultImpl modSet = resolveModSet("valid", "groups");
		assertModPresent(modSet, "dep", "1.0.0");
		assertModPresent(modSet, "mod_one", "1.0.0");
		assertModPresent(modSet, "mod_two", "1.0.0");
		assertProvidedPresent(modSet, "dep", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void providedDepends() throws Exception {
		ModSolveResultImpl modSet = resolveModSet("valid", "provided_depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library-but-renamed", "1.0.0");
		assertProvidedPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void includedDep() throws Exception {
		ModSolveResultImpl modSet = resolveModSet("valid", "included_dep");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void altDep() throws Exception {
		// Run the test multiple times to ensure we always pick the right dep
		// (and also makes sure we never mess up)
		for (int i = 0; i < 1000; i++) {
			if (i % 100 == 0) {
				System.out.println("ModResolvingTests.altDep iteration " + i);
			}
			ModSolveResultImpl modSet = resolveModSet("valid", "alt_deps");

			assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
			assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
			assertModPresent(modSet, "mod-resolving-tests-library", "2.0.0");
			assertNoMoreMods(modSet);
		}
	}

    @Test
    public void quilt() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "quilt");

        assertModPresent(modSet, "mod-resolving-tests-quilt", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void dep_gte() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "dep_gte");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void extraLibs() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "extra_libs");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertModMissing(modSet, "mod-resolving-tests-library");
        assertNoMoreMods(modSet);
    }

    @Test
    public void subMod() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "sub_mod");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertModPresent(modSet, "mod-resolving-tests-sub", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void quiltLoadType() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "quilt_load_type");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void quiltIncludedDep() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "quilt_included_dep");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void quiltIncludedProvided() throws Exception {
        ModSolveResultImpl modSet = resolveModSet("valid", "quilt_included_provided");

        assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
        assertModPresent(modSet, "mod-resolving-tests-better-library", "1.0.0");
        assertProvidedPresent(modSet, "mod-resolving-tests-library", "1.0.0");
        assertNoMoreMods(modSet);
    }

    @Test
    public void breaksError() {
    	resolveErrorSet("breaks");
    }

    @Test
    public void multiBreaksError() {
        resolveErrorSet("multi_breaks");
    }

    private static void resolveErrorSet(String subpath) {
    	try {
    		ModSolveResultImpl result = resolveModSet("error", subpath);

    		StringBuilder sb = new StringBuilder();
    		sb.append("Incorrectly resolved an invalid mod set!\n");

    		for (Entry<String, ModCandidateCls> entry : result.modMap.entrySet()) {
    			sb.append("  - '" + entry.getKey() + "' loaded from " + entry.getValue().getOriginUrl() + "\n");
    		}

    		for (Entry<String, ModCandidateCls> entry : result.providedMap.entrySet()) {
    			sb.append("  - '" + entry.getKey() + "' provided from " + entry.getValue().getOriginUrl() + "\n");
    		}

    		Assertions.fail(sb.toString());
    	} catch (ModSolvingException ignored) {
    		// Correct
    	} catch (ModResolutionException setupError) {
    		Assertions.fail("Failed to read the mod set!", setupError);
    	}
    }

	private static ModSolveResultImpl resolveModSet(String type, String subpath) throws ModResolutionException {

		Path modRoot = testLocation.resolve(type).resolve(subpath);

		ModResolver resolver = new ModResolver(LOGGER, true, modRoot);
		resolver.addCandidateFinder(new DirectoryModCandidateFinder(modRoot, false));
		return resolver.resolve(null);
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertModPresent(ModSolveResultImpl result, String modid, String version) {
		ModCandidateCls mod = result.modMap.remove(modid);

		if (mod == null) {
			Assertions.fail(modid + " is missing from " + result.modMap);
		} else {
			Assertions.assertEquals(version, mod.getInfo().getVersion().getFriendlyString());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertModMissing(ModSolveResultImpl result, String modid) {
		ModCandidateCls mod = result.modMap.remove(modid);

		if (mod != null) {
			Assertions.fail(modid + " is not missing, and instead is loaded: " + mod);
		}
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertProvidedPresent(ModSolveResultImpl result, String modid, String version) {
		ModCandidateCls mod = result.providedMap.remove(modid);

		if (mod == null) {
			Assertions.fail(modid + " is missing from " + result.providedMap);
		} else {
			Assertions.assertEquals(version, mod.getInfo().getVersion().getFriendlyString());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertProvidedMissing(ModSolveResultImpl result, String modid) {
		ModCandidateCls mod = result.providedMap.remove(modid);

		if (mod != null) {
			Assertions.fail(modid + " is not missing, and instead is provided by: " + mod);
		}
	}

	private static void assertNoMoreMods(ModSolveResultImpl result) {
		if (!result.modMap.isEmpty()) {
			Assertions.fail("Expected to find no more mods loaded, but found: " + result.modMap);
		}
		if (!result.providedMap.isEmpty()) {
			Assertions.fail("Expected to find no more provided mods loaded, but found: " + result.providedMap);
		}
	}
}
