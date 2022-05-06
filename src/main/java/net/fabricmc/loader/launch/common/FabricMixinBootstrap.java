/*
 * Copyright 2016 FabricMC
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

package net.fabricmc.loader.launch.common;

import net.fabricmc.api.EnvType;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModContainer.BasicSourceType;
import org.quiltmc.loader.impl.ModContainerImpl;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import net.fabricmc.mapping.tree.TinyTree;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() { }

	private static boolean initialized = false;

	static void addConfiguration(String configuration) {
		Mixins.addConfiguration(configuration);
	}

	static Set<String> getMixinConfigs(QuiltLoaderImpl loader, EnvType type) {
		return loader.getAllMods().stream()
				.map(ModContainer::metadata)
				.filter((m) -> m instanceof FabricLoaderModMetadata)
				.flatMap((m) -> ((FabricLoaderModMetadata) m).getMixinConfigs(type).stream())
				.filter(s -> s != null && !s.isEmpty())
				.collect(Collectors.toSet());
	}

	public static void init(EnvType side, QuiltLoaderImpl loader) {
		if (initialized) {
			throw new IllegalStateException("FabricMixinBootstrap has already been initialized!");
		}

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration mappingConfiguration = FabricLauncherBase.getLauncher().getMappingConfiguration();
			TinyTree mappings = mappingConfiguration.getMappings();

			if (mappings != null) {
				List<String> namespaces = mappings.getMetadata().getNamespaces();

				if (namespaces.contains("intermediary") && namespaces.contains(mappingConfiguration.getTargetNamespace())) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, "intermediary", mappingConfiguration.getTargetNamespace());
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						Log.info(LogCategory.MIXIN, "Loaded Quilt development mappings for mixin remapper!");
					} catch (Exception e) {
						Log.error(LogCategory.MIXIN, "Quilt development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				}
			}
		}

		MixinBootstrap.init();
		getMixinConfigs(loader, side).forEach(FabricMixinBootstrap::addConfiguration);

		Map<String, ModContainerImpl> configToModMap = new HashMap<>();

		for (ModContainerImpl mod : loader.getMods()) {
			for (String config : mod.getInternalMeta().mixins(side)) {
				ModContainerImpl prev = configToModMap.putIfAbsent(config, mod);
				if (prev != null) throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s",
						config, prev.metadata().id(), mod.metadata().id()));

				try {
					Mixins.addConfiguration(config);
				} catch (Throwable t) {
					throw new RuntimeException(String.format("Error creating Mixin config %s for mod %s", config, mod.metadata().id()), t);
				}
			}
		}

		for (Config config : Mixins.getConfigs()) {
			ModContainerImpl mod = configToModMap.get(config.getName());
			if (mod == null) continue;
		}

		try {
			IMixinConfig.class.getMethod("decorate", String.class, Object.class);
			MixinConfigDecorator.apply(configToModMap);
		} catch (NoSuchMethodException e) {
			Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
		}

		initialized = true;
	}

	public static final class MixinConfigDecorator {
		private static final List<LoaderMixinVersionEntry> versions = new ArrayList<>();

		static {
			// maximum loader version and bundled fabric mixin version, DESCENDING ORDER, LATEST FIRST
			// loader versions with new mixin versions need to be added here

			// addVersion("0.13", FabricUtil.COMPATIBILITY_0_11_0); // example for next entry (latest first!)
			addVersion("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0);
		}

		static void apply(Map<String, ModContainerImpl> configToModMap) {
			for (Config rawConfig : Mixins.getConfigs()) {
				ModContainerImpl mod = configToModMap.get(rawConfig.getName());
				if (mod == null) continue;

				IMixinConfig config = rawConfig.getConfig();
				config.decorate(FabricUtil.KEY_MOD_ID, mod.metadata().id());
				config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod));
			}
		}

		public static int getMixinCompat(ModContainerImpl mod) {
			// infer from loader dependency by determining the least relevant loader version the mod accepts
			// AND any loader deps

			List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

			if (mod.getSourceType() != BasicSourceType.NORMAL_FABRIC) {
				// quilt or builtin mod, we can assume it uses latest compat
				Log.debug(LogCategory.MIXIN, "Assuming Quilt mod %s uses latest mixin compatibility", mod.metadata().id());
				return FabricUtil.COMPATIBILITY_LATEST;
			}

			for (ModDependency dep : mod.getInternalMeta().asFabricModMetadata().getDependencies()) {
				if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
					if (dep.getKind() == ModDependency.Kind.DEPENDS) {
						reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
					} else if (dep.getKind() == ModDependency.Kind.BREAKS) {
						reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
					}
				}
			}

			if (reqIntervals.isEmpty()) throw new IllegalStateException("mod "+mod+" is incompatible with every loader version?"); // shouldn't get there

			Version minLoaderVersion = reqIntervals.get(0).getMin(); // it is sorted, to 0 has the absolute lower bound

			// Quilt: If we can't determine the minimum loader version, we prefer the latest compatibility
			// instead of the lowest one.

			if (minLoaderVersion != null) { // has a lower bound
				for (LoaderMixinVersionEntry version : versions) {
					if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
						Log.debug(LogCategory.MIXIN, "Mod %s requires loader version %s, using mixin compatibility %s", mod.metadata().id(), minLoaderVersion, version.mixinVersion);
						return version.mixinVersion;
					} else {
						Log.debug(LogCategory.MIXIN, "Mod %s requires loader version %s, using 0.9.2 mixin compatability", mod.metadata().id(), minLoaderVersion);
						return FabricUtil.COMPATIBILITY_0_9_2;
					}
				}
			}

			// Mod doesn't declare a dependency on a loader version; use oldest mixin compat version
			Log.debug(LogCategory.MIXIN, "Mod %s doesn't declare a dependency on a loader version, using 0.9.2 mixin compatability", mod.metadata().id());
			return FabricUtil.COMPATIBILITY_0_9_2;
		}

		private static void addVersion(String minLoaderVersion, int mixinCompat) {
			try {
				versions.add(new LoaderMixinVersionEntry(SemanticVersion.parse(minLoaderVersion), mixinCompat));
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		private static final class LoaderMixinVersionEntry {
			final SemanticVersion loaderVersion;
			final int mixinVersion;

			LoaderMixinVersionEntry(SemanticVersion loaderVersion, int mixinVersion) {
				this.loaderVersion = loaderVersion;
				this.mixinVersion = mixinVersion;
			}
		}
	}
}
