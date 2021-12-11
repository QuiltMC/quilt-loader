package org.quiltmc.loader.api.plugin;

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import org.quiltmc.loader.impl.metadata.qmj.ModLoadType;
import org.quiltmc.loader.impl.metadata.qmj.ModProvided;

public interface FullModMetadata extends ModMetadata {
	Collection<ModProvided> provides();

	ModLoadType loadType();

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints();

	@Nullable
	ModPlugin plugin();

	Collection<String> jars();

	Map<String, String> languageAdapters();

	Collection<String> repositories();

	/** @return True if the builtin quilt plugin should generate {@link Rule}s based on the {@link #depends()} and
	 *         {@link #breaks()} of this metadata. */
	boolean isQuiltDeps();

	/** @return True if the field is present, and the returned value for that field is the same as the future mod that
	 *         will replace this {@link TentativeLoadOption}. */
	boolean hasField(ModMetadataField field);

	/** @return A {@link ModMetadataBuilder} that is NOT tentative. */
	ModMetadataBuilder copyToBuilder();

	/** @return A {@link ModMetadataBuilder} that is NOT tentative. */
	ModMetadataBuilder copyToBuilder(LoaderValue.LObject root, String id, String group, Version version);

	public interface ModPlugin {
		String pluginClass();

		Collection<String> packages();
	}

	public enum ModMetadataField {
		ID(false, true),
		GROUP(false, true),
		VERSION(false, true),
		DEPENDS(true, true),
		BREAKS(true, true),
		PROVIDES(true, true),
		LOAD_TYPE(true, true),
		PLUGINS(true, true),
		JARS(true, true),
		REPOSITORIES(true, true),
		NAME(true, false),
		DESCRIPTION(true, false),
		LICENSES(true, false),
		CONTRIBUTORS(true, false),
		CONTACT_INFO(true, false),
		ICON(true, false),
		ENTRYPOINTS(true, false),
		LANGUAGE_ADAPTERS(true, false);

		public final boolean optional;
		public final boolean neededForSolving;

		ModMetadataField(boolean optional, boolean neededForSolving) {
			this.optional = optional;
			this.neededForSolving = neededForSolving;
		}
	}
}
