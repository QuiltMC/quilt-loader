/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.impl.transformer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.objectweb.asm.commons.Remapper;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.ManifestUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
final class RuntimeModRemapper {
	private static final String REMAP_TYPE_MANIFEST_KEY = "Fabric-Loom-Mixin-Remap-Type";
	private static final String REMAP_TYPE_STATIC = "static";

	static final boolean COPY_ON_WRITE = true;

	public static void remap(TransformCache cache) {
		List<ModLoadOption> modsToRemap = cache.getModsInCache().stream()
				.filter(modLoadOption -> modLoadOption.namespaceMappingFrom() != null)
				.collect(Collectors.toList());
		Set<InputTag> remapMixins = new HashSet<>();

		if (modsToRemap.isEmpty()) {
			return;
		}

		QuiltLauncher launcher = QuiltLauncherBase.getLauncher();

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(launcher.getMappingConfiguration().getMappings(), "intermediary", launcher.getTargetNamespace()))
				.renameInvalidLocals(false)
				.extension(new MixinExtension(remapMixins::contains))
				.build();

		try {
			remapper.readClassPathAsync(getRemapClasspath().toArray(new Path[0]));
		} catch (IOException e) {
			throw new RuntimeException("Failed to populate remap classpath", e);
		}

		try {
			Map<ModLoadOption, RemapInfo> infoMap = new HashMap<>();

			for (ModLoadOption mod : modsToRemap) {
				RemapInfo info = new RemapInfo();
				infoMap.put(mod, info);
				InputTag tag = remapper.createInputTag();
				info.tag = tag;
				info.inputPath = mod.resourceRoot().toAbsolutePath();

				if (requiresMixinRemap(info.inputPath)) {
					remapMixins.add(tag);
				}

				remapper.readInputsAsync(tag, info.inputPath);
			}

			//Done in a 2nd loop as we need to make sure all the inputs are present before remapping
			for (ModLoadOption mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				info.outputPath = cache.getRoot(mod);
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				info.outputConsumerPath = outputConsumer;

				remapper.apply(outputConsumer, info.tag);
			}

			//Done in a 3rd loop as this can happen when the remapper is doing its thing.
			for (ModLoadOption mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				if (!mod.metadata().accessWideners().isEmpty()) {
					info.accessWideners = new HashMap<>();
					for (String accessWidener : mod.metadata().accessWideners()) {
						info.accessWideners.put(accessWidener, remapAccessWidener(Files.readAllBytes(info.inputPath.resolve(accessWidener)), remapper.getRemapper()));
					}
				}
			}

			remapper.finish();

			for (ModLoadOption mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);

				info.outputConsumerPath.close();

				if (info.accessWideners != null) {
					for (Map.Entry<String, byte[]> entry : info.accessWideners.entrySet()) {
						Files.write(info.outputPath.resolve(entry.getKey()), entry.getValue());
					}
				}
			}

		} catch (IOException e) {
			remapper.finish();
			throw new RuntimeException("Failed to remap mods", e);
		}
	}

	private static byte[] remapAccessWidener(byte[] input, Remapper remapper) {
		AccessWidenerWriter writer = new AccessWidenerWriter();
		AccessWidenerRemapper remappingDecorator = new AccessWidenerRemapper(writer, remapper, "intermediary", QuiltLauncherBase.getLauncher().getTargetNamespace());
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(remappingDecorator);
		accessWidenerReader.read(input, "intermediary");
		return writer.write();
	}

	private static List<Path> getRemapClasspath() throws IOException {
		String remapClasspathFile = System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE);

		if (remapClasspathFile == null) {
			throw new RuntimeException("No remapClasspathFile provided");
		}

		String content = new String(Files.readAllBytes(Paths.get(remapClasspathFile)), StandardCharsets.UTF_8);

		return Arrays.stream(content.split(File.pathSeparator))
				.map(Paths::get)
				.collect(Collectors.toList());
	}

	private static boolean requiresMixinRemap(Path inputPath) throws IOException {
		final Manifest manifest = ManifestUtil.readManifest(inputPath);
		if (manifest == null) {
			return false;
		}
		final Attributes mainAttributes = manifest.getMainAttributes();
		return REMAP_TYPE_STATIC.equalsIgnoreCase(mainAttributes.getValue(REMAP_TYPE_MANIFEST_KEY));
	}

	private static class RemapInfo {
		InputTag tag;
		Path inputPath;
		Path outputPath;
		OutputConsumerPath outputConsumerPath;
		Map<String, byte[]> accessWideners;
	}
}
