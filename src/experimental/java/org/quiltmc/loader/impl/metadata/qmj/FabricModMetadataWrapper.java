package org.quiltmc.loader.impl.metadata.qmj;

import java.util.*;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.*;
import org.quiltmc.loader.impl.metadata.EntrypointMetadata;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;
import org.quiltmc.loader.impl.util.version.FabricSemanticVersionImpl;
import org.quiltmc.loader.impl.util.version.StringVersion;
import org.quiltmc.loader.impl.util.version.VersionPredicateParser;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.VersionPredicate;
import net.fabricmc.loader.api.metadata.Person;

import net.fabricmc.api.EnvType;

public class FabricModMetadataWrapper implements InternalModMetadata {
	public static final String GROUP = "loader.fabric";
	private static final String NO_LOCATION = "location not supported";
	private final LoaderModMetadata fabricMeta;
	private final Version version;
	private final Collection<ModDependency> depends, breaks;
	private final Collection<ModLicense> licenses;
	private final Collection<ModContributor> contributors;
	private final List<String> jars;
	private final Map<String, LoaderValue> customValues;
	private final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints;
	private final List<ModProvided> provides;

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
		List<String> jars = new ArrayList<>();
		for (NestedJarEntry entry : fabricMeta.getJars()) {
			jars.add(entry.getFile());
		}
		this.jars = Collections.unmodifiableList(jars);

		HashMap<String, LoaderValue> customValues = new HashMap<>();
		fabricMeta.getCustomValues().forEach((key, value) -> customValues.put(key, convertCustomValue(value)));
		this.customValues = Collections.unmodifiableMap(customValues);

		Map<String, Collection<AdapterLoadableClassEntry>> e = new HashMap<>();
		for (String key : fabricMeta.getEntrypointKeys()) {
			Collection<AdapterLoadableClassEntry> c = new ArrayList<>();
			for (EntrypointMetadata entrypoint : fabricMeta.getEntrypoints(key)) {
				c.add(new AdapterLoadableClassEntry(entrypoint.getAdapter(), entrypoint.getValue()));
			}
			e.put(key, Collections.unmodifiableCollection(c));
		}
		this.entrypoints = Collections.unmodifiableMap(e);

		List<ModProvided> p = new ArrayList<>();
		for (String provided : fabricMeta.getProvides()) {
			p.add(new ModProvided("", provided, this.version));
		}
		this.provides = Collections.unmodifiableList(p);
	}

	private LoaderValue convertCustomValue(CustomValue customValue) {
		switch (customValue.getType()) {
			case OBJECT:
				Map<String, LoaderValue> map = new HashMap<>();

				for (Map.Entry<String, CustomValue> cvEntry : customValue.getAsObject()) {
					map.put(cvEntry.getKey(), convertCustomValue(cvEntry.getValue()));
				}

				return new JsonLoaderValue.ObjectImpl(NO_LOCATION, map);
			case ARRAY:
				CustomValue.CvArray arr = customValue.getAsArray();
				List<LoaderValue> values = new ArrayList<>(arr.size());
				for (CustomValue value : arr) {
					values.add(convertCustomValue(value));
				}
				return new JsonLoaderValue.ArrayImpl(NO_LOCATION, values);
			case STRING:
				return new JsonLoaderValue.StringImpl(NO_LOCATION, customValue.getAsString());
			case NUMBER:
				return new JsonLoaderValue.NumberImpl(NO_LOCATION, customValue.getAsNumber());
			case BOOLEAN:
				return new JsonLoaderValue.BooleanImpl(NO_LOCATION, customValue.getAsBoolean());
			case NULL:
				return new JsonLoaderValue.NullImpl(NO_LOCATION);
			default:
				throw new IllegalStateException("Unexpected custom value type " + customValue.getType());
		}
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
		return fabricMeta.getContact().get(key).orElse(null);
	}

	@Override
	public Map<String, String> contactInfo() {
		return fabricMeta.getContact().asMap();
	}

	@Override
	public Collection<ModDependency> depends() {
		return depends;
	}

	@Override
	public Collection<ModDependency> breaks() {
		return breaks;
	}

	@Override
	public ModLoadType loadType() {
		return ModLoadType.IF_POSSIBLE;
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
						if (type() == Type.ANY) {
							return true;
						}

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

					@Override
					public String toString() {
						return type.prefix() + predicate.getVersion();
					}
				});
			}
			out.add(new ModDependencyImpl.OnlyImpl("Fabric Dep 1", new ModDependencyIdentifierImpl(f.getModId()), constraints, null, false, null));
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
	public @Nullable String icon(int size) {
		return fabricMeta.getIconPath(size).orElse(null);
	}

	@Override
	public boolean containsValue(String key) {
		return customValues.containsKey(key);
	}

	@Override
	public @Nullable LoaderValue value(String key) {
		return customValues.get(key);
	}

	@Override
	public Map<String, LoaderValue> values() {
		return customValues;
	}

	@Override
	public Collection<String> mixins(EnvType env) {
		return fabricMeta.getMixinConfigs(env);
	}

	@Override
	public Collection<String> accessWideners() {
		String acc = fabricMeta.getAccessWidener();
		return acc == null ? Collections.emptyList() : Collections.singleton(acc);
	}

	@Override
	public ModEnvironment environment() {
		return fabricMeta.getEnvironment();
	}

	@Override
	public Collection<ModProvided> provides() {
		return provides;
	}

	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints() {
		return entrypoints;
	}

	@Override
	public Collection<AdapterLoadableClassEntry> getPlugins() {
		// fabric mods can't use the quilt plugin system
		return Collections.emptyList();
	}

	@Override
	public Collection<String> jars() {
		return jars;
	}

	@Override
	public Map<String, String> languageAdapters() {
		return fabricMeta.getLanguageAdapterDefinitions();
	}

	@Override
	public Collection<String> repositories() {
		return Collections.emptyList();
	}
}
