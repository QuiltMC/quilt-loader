package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataBuilder;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;
import org.quiltmc.loader.api.plugin.FullModMetadata.ModMetadataField;

import net.fabricmc.loader.api.metadata.ModEnvironment;

public final class V1ModMetadataBuilder implements ModMetadataBuilder {

	private static final EnumSet<ModMetadataField> DEFAULT_SET = EnumSet.of(
		ModMetadataField.ID, ModMetadataField.GROUP, ModMetadataField.VERSION
	);

	public final JsonLoaderValue.ObjectImpl root;
	public final String id;
	public final String group;
	public final Version version;

	@Nullable String name;
	@Nullable String description;
	final List<ModLicense> licenses = new ArrayList<>();
	final List<ModContributor> contributors = new ArrayList<>();
	final Map<String, String> contactInformation = new LinkedHashMap<>();
	final List<ModDependency> depends = new ArrayList<>();
	final List<ModDependency> breaks = new ArrayList<>();
	@Nullable Icons icons;
	ModLoadType loadType = ModLoadType.IF_REQUIRED;
	final Collection<ModProvided> provides = new ArrayList<>();
	final Map<String, List<AdapterLoadableClassEntry>> entrypoints = new LinkedHashMap<>();
	final Collection<AdapterLoadableClassEntry> plugins = new ArrayList<>();
	final List<String> jars = new ArrayList<>();
	final Map<String, String> languageAdapters = new LinkedHashMap<>();
	final Collection<String> repositories = new ArrayList<>();

	final Collection<String> mixins = new ArrayList<>();
	final Collection<String> accessWideners = new ArrayList<>();
	ModEnvironment env = ModEnvironment.UNIVERSAL;

	final @Nullable Set<ModMetadataField> fieldsPresent;

	private V1ModMetadataBuilder(LoaderValue.LObject root, String id, String group, Version version, boolean tentative) {
		this.root = (JsonLoaderValue.ObjectImpl) root;
		this.id = id;
		this.group = group;
		this.version = version;
		
		if (tentative) {
			fieldsPresent = EnumSet.copyOf(DEFAULT_SET);
		} else {
			fieldsPresent = null;
		}
	}

	public static ModMetadataBuilder of(LoaderValue.LObject root, String id, String group, Version version) {
		return new V1ModMetadataBuilder(root, id, group, version, false);
	}

	public static ModMetadataBuilder ofTentative(LoaderValue.LObject root, String id, String group, Version version) {
		return new V1ModMetadataBuilder(root, id, group, version, true);
	}

	@Override
	public ModMetadataBuilder name(String name) {
		this.name = name;
		fieldPresent();
		return this;
	}

	@Override
	public ModMetadataBuilder description(String description) {
		this.description = description;
	}

	public void addLicense(ModLicense license) {
		this.licenses.add(license);
	}

	public void addLicenses(Collection<ModLicense> licenses) {
		this.licenses.addAll(licenses);
	}

	public void addContributor(ModContributor contributor) {
		this.contributors.add(contributor);
	}

	public void addContributors(Collection<ModContributor> contributors) {
		this.contributors.addAll(contributors);
	}

	public void addContactInformation(String key, String contactInformation) {
		this.contactInformation.put(key, contactInformation);
	}

	public void putContactInformation(Map<String, String> contactInformation) {
		this.contactInformation.putAll(contactInformation);
	}

	public void addDepends(ModDependency depends) {
		this.depends.add(depends);
	}

	public void addDepends(Collection<ModDependency> depends) {
		this.depends.addAll(depends);
	}

	public void addBreaks(ModDependency breaks) {
		this.breaks.add(breaks);
	}

	public void addBreaks(Collection<ModDependency> breaks) {
		this.breaks.addAll(breaks);
	}

	public void icons(@Nullable Icons icons) {
		this.icons = icons;
	}

	public void loadTYpe(ModLoadType loadType) {
		this.loadType = loadType;
	}

	public void addProvides(ModProvided provides) {
		this.provides.add(provides);
	}

	public void addProvides(Collection<ModProvided> provides) {
		this.provides.addAll(provides);
	}

	public void setEntrypoints(Map<String, List<AdapterLoadableClassEntry>> entrypoints) {
		this.entrypoints = entrypoints;
	}

	// No plugin addition here
	// (only quilt.mod.json files can contain plugins anyway)

	public void addJar(String jar) {
		this.jars.add(jar);
	}

	public void addJars(Collection<String> jars) {
		this.jars.addAll(jars);
	}

	public void setLanguageAdapters(Map<String, String> languageAdapters) {
		this.languageAdapters = languageAdapters;
	}

	public void setRepositories(Collection<String> repositories) {
		this.repositories = repositories;
	}

	public void setMixins(Collection<String> mixins) {
		this.mixins = mixins;
	}

	public void setAccessWideners(Collection<String> accessWideners) {
		this.accessWideners = accessWideners;
	}

	public void env(ModEnvironment env) {
		this.env = env;
	}
}
