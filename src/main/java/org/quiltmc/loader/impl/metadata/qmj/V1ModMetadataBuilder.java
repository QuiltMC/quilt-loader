package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModLoadType;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;

import net.fabricmc.loader.api.metadata.ModEnvironment;

public class V1ModMetadataBuilder {
	/* Required fields */
	public ObjectImpl root = new ObjectImpl("root", new HashMap<>());
	public String id;
	public String group;
	public Version version;
	/* Optional fields */
	public @Nullable String name;
	public @Nullable String description;
	public final List<ModLicense> licenses = new ArrayList<>();
	public final List<ModContributor> contributors = new ArrayList<>();
	public final Map<String, String> contactInformation = new LinkedHashMap<>();
	public final List<ModDependency> depends = new ArrayList<>();
	public final List<ModDependency> breaks = new ArrayList<>();
	public String intermediateMappings;
	public @Nullable Icons icons;
	/* Internal fields */
	public ModLoadType loadType = ModLoadType.IF_REQUIRED;
	public final List<ProvidedMod> provides = new ArrayList<>();
	public final Map<String, List<AdapterLoadableClassEntry>> entrypoints = new LinkedHashMap<>();
	public final List<AdapterLoadableClassEntry> plugins = new ArrayList<>();
	public final List<String> jars = new ArrayList<>();
	public final Map<String, String> languageAdapters = new LinkedHashMap<>();
	public final List<String> repositories = new ArrayList<>();
	/* TODO: Move to plugins */
	public final List<String> mixins = new ArrayList<>();
	public final List<String> accessWideners = new ArrayList<>();
	public ModEnvironment env = ModEnvironment.UNIVERSAL;

	public InternalModMetadata build() {
		return new V1ModMetadataImpl(this);
	}

	public void setRoot(ObjectImpl root) {
		this.root = root;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public void setName(@Nullable String name) {
		this.name = name;
	}

	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	public void setIntermediateMappings(String intermediateMappings) {
		this.intermediateMappings = intermediateMappings;
	}

	public void setIcons(@Nullable Icons icons) {
		this.icons = icons;
	}

	public void setLoadType(ModLoadType loadType) {
		this.loadType = loadType;
	}

	public void setEnv(ModEnvironment env) {
		this.env = env;
	}
}
