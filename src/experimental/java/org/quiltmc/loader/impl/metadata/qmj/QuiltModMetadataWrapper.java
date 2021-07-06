package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModDependency.Only;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.impl.metadata.EntrypointMetadata;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.VersionPredicate;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

import net.fabricmc.api.EnvType;

public class QuiltModMetadataWrapper implements LoaderModMetadata {
	private final InternalModMetadata quiltMeta;
	private Version version;
	private Collection<ModDependency> depends, breaks;

	public QuiltModMetadataWrapper(InternalModMetadata quiltMeta) {
		this.quiltMeta = quiltMeta;
	}

	@Override
	public InternalModMetadata asQuiltModMetadata() {
		return quiltMeta;
	}

	@Override
	public String getType() {
		return "quilt";
	}

	@Override
	public String getId() {
		return quiltMeta.id();
	}

	@Override
	public Collection<String> getProvides() {
		// FIXME: Implement provides properly!
		return Collections.emptySet();
	}

	@Override
	public Version getVersion() {
		if (version == null) {
			try {
				version = Version.parse(quiltMeta.version().raw());
			} catch (VersionParsingException e) {
				throw new Error(e);
			}
		}
		return version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<ModDependency> getDepends() {
		if (depends == null) {
			depends = genDeps(quiltMeta.depends());
		}
		return depends;
	}

	@Override
	public Collection<ModDependency> getRecommends() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getSuggests() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getConflicts() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getBreaks() {
		if (breaks == null) {
			breaks = genDeps(quiltMeta.breaks());
		}
		return breaks;
	}

	private static Collection<ModDependency> genDeps(Collection<org.quiltmc.loader.api.ModDependency> from) {
		List<ModDependency> to = new ArrayList<>();

		for (org.quiltmc.loader.api.ModDependency qDep : from) {

			if (qDep instanceof org.quiltmc.loader.api.ModDependency.Any) {
				// Literally nothing we can do about this
				continue;
			}

			org.quiltmc.loader.api.ModDependency.Only on = (org.quiltmc.loader.api.ModDependency.Only) qDep;

			to.add(new ModDependency() {
				@Override
				public boolean matches(Version version) {
					org.quiltmc.loader.api.Version quiltVer;

					if (version instanceof org.quiltmc.loader.api.Version) {
						quiltVer = (org.quiltmc.loader.api.Version) version;
					} else {
						quiltVer = org.quiltmc.loader.api.Version.of(version.getFriendlyString());
					}

					return on.matches(quiltVer);
				}

				@Override
				public Set<VersionPredicate> getVersionRequirements() {
					throw new UnsupportedOperationException("// TODO: Implement this!");
				}

				@Override
				public String getModId() {
					return on.id().id();
				}
			});
		}

		return to;
	}

	@Override
	public String getName() {
		return quiltMeta.name();
	}

	@Override
	public String getDescription() {
		return quiltMeta.description();
	}

	@Override
	public Collection<Person> getAuthors() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<Person> getContributors() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public ContactInformation getContact() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> getLicense() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Optional<String> getIconPath(int size) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public boolean containsCustomValue(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public @Nullable CustomValue getCustomValue(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public boolean containsCustomElement(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public JsonElement getCustomElement(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	// Fabric's internal ModMetadata

	private static UnsupportedOperationException internalError() {
		throw new UnsupportedOperationException("Fabric-internal metadata is not exposed for quilt mods - since only quilt loader itself may use this.");
	}

	@Override
	public int getSchemaVersion() {
		throw internalError();
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		throw internalError();
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		throw internalError();
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		throw internalError();
	}

	@Override
	public @Nullable String getAccessWidener() {
		throw internalError();
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		throw internalError();
	}

	@Override
	public Collection<String> getOldInitializers() {
		throw internalError();
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		throw internalError();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		throw internalError();
	}

	@Override
	public void emitFormatWarnings(Logger logger) {

	}
}
