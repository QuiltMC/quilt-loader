package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.util.version.FabricSemanticVersionImpl;
import org.quiltmc.loader.impl.util.version.StringVersion;
import org.quiltmc.loader.impl.util.version.VersionPredicateParser;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.VersionPredicate;

public class FabricModMetadataWrapper implements InternalModMetadata {
	public static final String GROUP = "loader.fabric";

	private final LoaderModMetadata fabricMeta;
	private final Version version;
	private final Collection<ModDependency> depends, breaks;
	private final Collection<ModLicense> licenses;
	private final Collection<ModContributor> contributors;
	public FabricModMetadataWrapper(LoaderModMetadata fabricMeta) {
		this.fabricMeta = fabricMeta;
		net.fabricmc.loader.api.Version fabricVersion = fabricMeta.getVersion();
		if (fabricVersion instanceof StringVersion) {
			this.version = Version.of(fabricVersion.getFriendlyString());
		} else {
			this.version = (FabricSemanticVersionImpl) fabricVersion;
		}
		this.depends = genDepends(fabricMeta.getDepends());
		this.breaks = genDepends(fabricMeta.getBreaks());
		this.licenses = Collections.unmodifiableCollection(fabricMeta.getLicense().stream().map(ModLicenseImpl::fromIdentifierOrDefault).collect(Collectors.toList()));
		this.contributors = convertContributors(fabricMeta);
	}

	@Override
	public LoaderModMetadata asFabricModMetadata() {
		return fabricMeta;
	}

	@Override
	public String id() {
		return fabricMeta.getId();
	}

	@Override
	public String group() {
		return GROUP;
	}

	@Override
	public Version version() {
		return version;
	}

	@Override
	public String name() {
		return fabricMeta.getName();
	}

	@Override
	public String description() {
		return fabricMeta.getDescription();
	}

	@Override
	public Collection<ModLicense> licenses() {
		return licenses;
	}

	@Override
	public Collection<ModContributor> contributors() {
		return contributors;
	}

	@Override
	public @Nullable String getContactInfo(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Map<String, String> contactInfo() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<ModDependency> depends() {
		return depends;
	}

	@Override
	public Collection<ModDependency> breaks() {
		return breaks;
	}

	private static Collection<ModDependency> genDepends(Collection<net.fabricmc.loader.api.metadata.ModDependency> from) {
		List<ModDependency> out = new ArrayList<>();
		for (net.fabricmc.loader.api.metadata.ModDependency f : from) {
			Collection<VersionConstraint> constraints = new ArrayList<>();
			for (VersionPredicate predicate : f.getVersionRequirements()) {
				VersionConstraint.Type type = convertType(predicate.getType());
				constraints.add(new VersionConstraint() {
					@Override
					public String version() {
						return predicate.getVersion();
					}

					@Override
					public Type type() {
						return type;
					}

					@Override
					public boolean matches(Version version) {
						try {

							net.fabricmc.loader.api.Version fVersion;

							if (version.isSemantic()) {
								fVersion = new FabricSemanticVersionImpl(version.semantic());
							} else {
								fVersion = new StringVersion(version.raw());
							}

							return VersionPredicateParser.matches(fVersion, version());
						} catch (VersionParsingException e) {
							return false;
						}
					}
				});
			}
			out.add(new ModDependencyImpl.OnlyImpl(new ModDependencyIdentifierImpl(f.getModId()), constraints, null, false, null));
		}
		return Collections.unmodifiableList(Arrays.asList(out.toArray(new ModDependency[0])));
	}

	private static VersionConstraint.Type convertType(net.fabricmc.loader.api.VersionPredicate.Type type) {
		switch (type) {
			default:
				return VersionConstraint.Type.valueOf(type.name());
		}
	}

	private static Collection<ModContributor> convertContributors(LoaderModMetadata metadata) {
		List<ModContributor> contributors = new ArrayList<>();
		for (Person author : metadata.getAuthors()) {
			contributors.add(new ModContributorImpl(author.getName(), "Author"));
		}
		for (Person contributor : metadata.getContributors()) {
			contributors.add(new ModContributorImpl(contributor.getName(), "Contributor"));
		}
		return Collections.unmodifiableList(contributors);
	}

	@Override
	public @Nullable String getIcon(int size) {
		return fabricMeta.getIconPath(size).orElse(null);
	}

	@Override
	public boolean containsRootValue(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public @Nullable LoaderValue getValue(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Map<String, LoaderValue> values() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> mixins() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> accessWideners() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<?> provides() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints(String key) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<AdapterLoadableClassEntry> getPlugins() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> jars() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Map<String, String> languageAdapters() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Collection<String> repositories() {
		return Collections.emptyList();
	}
}
