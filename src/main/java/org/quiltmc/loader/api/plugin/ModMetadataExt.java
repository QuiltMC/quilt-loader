package org.quiltmc.loader.api.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

/** Additional metadata that should be implemented by plugin-provided mods that wish to rely on quilt's solver to
 * implement provides or dependency handling. */
public interface ModMetadataExt extends ModMetadata {

	// Dependency handling

	/** @return True if quilt-loader should use {@link #depends()} and {@link #breaks()} to generate {@link Rule}s for
	 *         the solver, or false if your plugin should handle them instead. */
	default boolean shouldQuiltDefineDependencies() {
		return false;
	}

	/** @return True if quilt-loader should use {@link #provides()} to generate {@link LoadOption}s for the solver, or
	 *         false if your plugin should handle them instead. */
	default boolean shouldQuiltDefineProvides() {
		return false;
	}

	default ModLoadType loadType() {
		return ModLoadType.ALWAYS;
	}

	default Collection<? extends ProvidedMod> provides() {
		return Collections.emptyList();
	}

	@Nullable
	default ModPlugin plugin() {
		return null;
	}

	public enum ModLoadType {
		ALWAYS,
		IF_POSSIBLE,
		IF_REQUIRED;
	}

	public interface ProvidedMod {
		String group();

		String id();

		Version version();
	}

	public interface ModPlugin {
		String pluginClass();

		Collection<String> packages();
	}

	// Runtime

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints();

	Map<String, String> languageAdapters();
}
