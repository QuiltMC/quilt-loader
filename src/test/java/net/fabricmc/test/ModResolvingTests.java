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

package net.fabricmc.test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map.Entry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.QuiltPluginManagerForTests;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModSolvingException;
import org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl;
import org.quiltmc.loader.impl.report.QuiltReportedError;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;

public final class ModResolvingTests {

	private static Path testLocation;

	@BeforeAll
	public static void setupPaths() {
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
	public void sidedDepends() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "sided_depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void sidedProvidedDepends() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "sided_provided_depends");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void mavenGroups() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "groups");
		assertModPresent(modSet, "dep", "1.0.0");
		assertModPresent(modSet, "mod_one", "1.0.0");
		assertModPresent(modSet, "mod_two", "1.0.0");
		assertProvidedPresent(modSet, "dep", "1.0.0");
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
	public void lowBreaks() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "lowbreaks");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-optional", "1.19-1.0.6");
		assertNoMoreMods(modSet);
	}

	@Test
	public void includedDuelProvidedDep() throws Exception {
		// Run the test multiple times to ensure we always pick the right dep
		// (and also makes sure we never mess up)
		for (int i = 0; i < 1000; i++) {
			if (i % 100 == 0) {
				System.out.println("ModResolvingTests.includedDuelProvidedDep iteration " + i);
			}
			ModSolveResult modSet = resolveModSet("valid", "included_duel_provided_dep");

			assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
			assertModPresent(modSet, "mod-resolving-tests-bookshelf", "1.1.0");
			assertProvidedPresent(modSet, "mod-resolving-tests-library", "1.1.0");
			assertNoMoreMods(modSet);
		}
	}

	@Test
	public void altDep() throws Exception {
		// Run the test multiple times to ensure we always pick the right dep
		// (and also makes sure we never mess up)
		for (int i = 0; i < 1000; i++) {
			if (i % 100 == 0) {
				System.out.println("ModResolvingTests.altDep iteration " + i);
			}
			ModSolveResult modSet = resolveModSet("valid", "alt_deps");

			assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
			assertModPresent(modSet, "mod-resolving-tests-other", "1.0.0");
			assertModPresent(modSet, "mod-resolving-tests-library", "2.0.0");
			assertNoMoreMods(modSet);
		}
	}

	@Test
	public void quilt() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt");

		assertModPresent(modSet, "mod-resolving-tests-quilt", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void dep_gte() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "dep_gte");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void extraLibs() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "extra_libs");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModMissing(modSet, "mod-resolving-tests-library");
		assertNoMoreMods(modSet);
	}

	@Test
	public void subMod() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "sub_mod");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-sub", "1.0.0");
		assertNoMoreMods(modSet);
	}

	// This currently fails
	// @Test
	// public void multiTarget() throws Exception {
	// ModSolveResult modSet = resolveModSet("valid", "multi_target");
	//
	// assertModPresent(modSet, "minecraft", "1.18.2");
	// assertModPresent(modSet, "mod-resolving-tests-library", "2.0.0");
	// assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
	// assertNoMoreMods(modSet);
	// }

	@Test
	public void testJijProvided() throws ModResolutionException {
		ModSolveResult modSet = resolveModSet("valid", "jij_provided");

		assertModPresent(modSet, "number_overhaul", "1.0.0");
		assertModPresent(modSet, "uwu-lib", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltLoadType() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_load_type");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltLoadTypeAlways() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_load_type_always");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltIncludedDep() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_included_dep");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltGroupDep() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_group_dep");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltIncludedProvided() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_included_provided");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-better-library", "1.0.0");
		assertProvidedPresent(modSet, "mod-resolving-tests-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void quiltIncludedOptional() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "quilt_included_optional");

		assertModPresent(modSet, "mod-resolving-tests-main", "1.0.0");
		assertModPresent(modSet, "mod-resolving-tests-optional-library", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void qslProvidedOverFapi() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "qsl_provides");

		for (Entry<String, ModLoadOption> entry : modSet.directMods().entrySet()) {
			System.out.println(entry);
		}

		for (Entry<String, ModLoadOption> entry : modSet.providedMods().entrySet()) {
			System.out.println(entry);
		}

		assertModPresent(modSet, "a_mod", "1.0.0");
		assertModPresent(modSet, "quilt-standard-libraries", "1.0.0");

		// The mod set (as provided) doesn't narrow this down enough
		// so either mods being present would be okay as far as the solver is concerned
		if (modSet.directMods().containsKey("quilt-crash-handler")) {
			assertModPresent(modSet, "quilt-crash-handler", "1.0.0");
			assertProvidedPresent(modSet, "fabric-crash-handler", "1.0.0");
		} else {
			assertModPresent(modSet, "fabric-crash-handler", "1.0.0");
		}
		assertNoMoreMods(modSet);
	}

	@Test
	public void unless() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "unless");

		assertModPresent(modSet, "mod_a", "1.0.0");
		assertModPresent(modSet, "mod_b", "1.0.0");
		assertModPresent(modSet, "mod_c", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void unlessProvided() throws Exception {
		ModSolveResult modSet = resolveModSet("valid", "unless_provided");

		assertModPresent(modSet, "mod_a", "1.0.0");
		assertModPresent(modSet, "mod_b", "1.0.0");
		assertProvidedPresent(modSet, "mod_c", "1.0.0");
		assertModPresent(modSet, "mod_d", "1.0.0");
		assertNoMoreMods(modSet);
	}

	@Test
	public void breaksError() {
		resolveErrorSet("breaks");
	}

	@Test
	public void breaksUnlessError() {
		resolveErrorSet("breaks_unless");
	}

	@Test
	public void multiBreaksError() {
		resolveErrorSet("multi_breaks");
	}

	@Test
	public void multiDependsError() {
		resolveErrorSet("multi_depends");
	}

	@Test
	public void dependsOnBreaksError() {
		resolveErrorSet("depends_on_breaks");
	}

	@Test
	public void dependsOnBrokenError() {
		resolveErrorSet("depends_on_broken");
	}

	@Test
	public void dependsOnProvidedExistingError() {
		resolveErrorSet("depends_on_provided_existing");
	}

	@Test
	public void providesExistingError() {
		resolveErrorSet("provides_existing");
	}

	@Test
	public void dependsError() {
		resolveErrorSet("depends");
	}

	@Test
	public void dependsArrayError() {
		resolveErrorSet("depends_array");
	}

	@Test
	public void complexDependsOnProvidingExistingError() {
		resolveErrorSet("complex/depends_on_providing_existing");
	}

	private static void resolveErrorSet(String subpath) {
		try {
			ModSolveResult result = resolveModSet("error", subpath);

			StringBuilder sb = new StringBuilder();
			sb.append("Incorrectly resolved an invalid mod set!\n");

			for (Entry<String, ModLoadOption> entry : result.directMods().entrySet()) {
				sb.append("  - '" + entry.getKey() + "' loaded from " + entry.getValue().getLoadSource() + "\n");
			}

			for (Entry<String, ModLoadOption> entry : result.providedMods().entrySet()) {
				sb.append("  - '" + entry.getKey() + "' provided from " + entry.getValue().getLoadSource() + "\n");
			}

			fail(sb.toString());
		} catch (ModResolutionException ignored) {
			// Correct
			System.err.println(ignored.getMessage());
		}
	}

	private static ModSolveResult resolveModSet(String type, String subpath) throws ModResolutionException {

		Path game = testLocation.resolve("game_dir");
		Path config = testLocation.resolve("config_dir");
		Path cache = testLocation.resolve(".cache");
		Path modRoot = testLocation.resolve(type).resolve(subpath);
		final ModSolveResultImpl result;

		QuiltPluginManagerImpl pluginManager = new QuiltPluginManagerForTests(game, config, modRoot, cache);

		try {
			result = pluginManager.run(false);

			return new ModSolveResultImpl(
				new HashMap<>(result.directModMap), //
				new HashMap<>(result.providedModMap), //
				new HashMap<>(result.extraResults), //
				new HashMap<>(result.unknownRegularFiles), //
				new HashMap<>(result.unknownFiles)//
			);
		} catch (QuiltReportedError error) {
			StringWriter writer = new StringWriter();
			error.report.write(new PrintWriter(writer));
			throw new ModResolutionException(writer.toString());
		}
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertModPresent(ModSolveResult result, String modid, String version) {
		ModLoadOption mod = result.directMods().remove(modid);

		if (mod == null) {
			fail(modid + " is missing from " + result.directMods());
		} else {
			Assertions.assertEquals(version, mod.version().raw());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertModMissing(ModSolveResult result, String modid) {
		ModLoadOption mod = result.directMods().remove(modid);

		if (mod != null) {
			fail(modid + " is not missing, and instead is loaded: " + mod);
		}
	}

	/** Asserts that the mod with the given ID is both present and is loaded with the specified version. This also
	 * removes the mod entry from the map. */
	private static void assertProvidedPresent(ModSolveResult result, String modid, String version) {
		ModLoadOption mod = result.providedMods().remove(modid);

		if (mod == null) {
			fail(modid + " is missing from " + result.providedMods());
		} else {
			Assertions.assertEquals(version, mod.version().raw());
		}
	}

	/** Asserts that the mod with the given ID is not loaded. This also removes the mod entry from the map. */
	private static void assertProvidedMissing(ModSolveResult result, String modid) {
		ModLoadOption mod = result.providedMods().remove(modid);

		if (mod != null) {
			fail(modid + " is not missing, and instead is provided by: " + mod);
		}
	}

	private static void assertNoMoreMods(ModSolveResult result) {
		if (!result.directMods().isEmpty()) {
			fail("Expected to find no more mods loaded, but found: " + result.directMods());
		}
		if (!result.providedMods().isEmpty()) {
			fail("Expected to find no more provided mods loaded, but found: " + result.providedMods());
		}
	}

	private static void fail(String msg) {
		System.err.println(msg);
		Assertions.fail(msg);
	}
}
