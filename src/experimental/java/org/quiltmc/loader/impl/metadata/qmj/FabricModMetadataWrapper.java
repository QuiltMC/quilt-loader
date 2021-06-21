package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.api.VersionConstraint.Type;
import org.quiltmc.loader.impl.VersionConstraintImpl;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.util.version.SemanticVersionImpl;
import org.quiltmc.loader.impl.util.version.StringVersion;
import org.quiltmc.loader.impl.util.version.VersionPredicateParser;
import org.spongepowered.asm.mixin.injection.Constant;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.VersionPredicate;
import net.fabricmc.loader.api.metadata.ModMetadata;

public class FabricModMetadataWrapper implements InternalModMetadata {
    public static final String GROUP = "loader.fabric";

    private final LoaderModMetadata fabricMeta;
    private Version version;
    private Collection<ModDependency> depends, breaks;

    public FabricModMetadataWrapper(LoaderModMetadata fabricMeta) {
        this.fabricMeta = fabricMeta;
    }

    @Override
    public ModMetadata asFabricModMetadata() {
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
        if (version == null) {
            net.fabricmc.loader.api.Version fabricVersion = fabricMeta.getVersion();
            if (fabricVersion instanceof StringVersion) {
                this.version = Version.of(fabricVersion.getFriendlyString());
            } else {
                this.version = Version.of(((org.quiltmc.loader.impl.util.version.SemanticVersionImpl) fabricVersion).originalVersion);
            }
        }
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
        // TODO Auto-generated method stub
        throw new AbstractMethodError("// TODO: Implement this!");
    }

    @Override
    public Collection<ModContributor> contributors() {
        // TODO Auto-generated method stub
        throw new AbstractMethodError("// TODO: Implement this!");
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
        if (depends == null) {
            depends = genDepends(fabricMeta.getDepends());
        }
        return depends;
    }

    @Override
    public Collection<ModDependency> breaks() {
        if (breaks == null) {
            breaks = genDepends(fabricMeta.getBreaks());
        }
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
								fVersion = new SemanticVersionImpl(version.raw(), true);
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
    public <T> T get(Class<T> type) throws IllegalArgumentException {
        return null;
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
