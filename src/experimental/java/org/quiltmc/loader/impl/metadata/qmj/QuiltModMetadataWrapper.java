package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.EntrypointMetadata;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

import net.fabricmc.api.EnvType;

public class QuiltModMetadataWrapper implements LoaderModMetadata {
	private final InternalModMetadata quiltMeta;
	private Version version;

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
		throw new UnsupportedOperationException("// TODO: Implement this!");
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
		throw new UnsupportedOperationException("// TODO: Implement this!");
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

	@Override
	public int getSchemaVersion() {
		return 1;
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return quiltMeta.jars().stream().map(j -> (NestedJarEntry) () -> j).collect(Collectors.toList());
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public @Nullable String getAccessWidener() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> getOldInitializers() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public void emitFormatWarnings(Logger logger) {

	}
}
