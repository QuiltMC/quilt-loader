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

package org.quiltmc.loader.impl.discovery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.objectweb.asm.commons.Remapper;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.MountOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
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

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class RuntimeModRemapper {

	static final boolean DISABLE_REMAP = false;
	static final boolean COPY_ON_WRITE = true;

	public static void remap(Path cache, List<ModLoadOption> modList) {
		List<ModLoadOption> modsToRemap = DISABLE_REMAP ? new ArrayList<>() : modList.stream()
				.filter(modLoadOption -> modLoadOption.namespaceMappingFrom() != null)
				.collect(Collectors.toList());

		// Copy everything that's not in the modsToRemap list
		for (ModLoadOption mod : modList) {
			boolean skipRemap = DISABLE_REMAP || mod.namespaceMappingFrom() == null;
			if (skipRemap && mod.needsChasmTransforming() && !QuiltLoaderImpl.MOD_ID.equals(mod.id())) {

				final boolean onlyTranformableFiles = mod.couldResourcesChange();

				Path modSrc = mod.resourceRoot();
				Path modDst = cache.resolve(mod.id());
				try {
					Files.walk(modSrc).forEach(path -> {
						if (!FasterFiles.isRegularFile(path)) {
							// Only copy class files, since those files are the only files modified by chasm
							if (!FasterFiles.isDirectory(path)) {
								System.out.println("Unregular file " + path.getClass() + " " + path);
							}
							return;
						}
						if (onlyTranformableFiles) {
							String fileName = path.getFileName().toString();
							if (!fileName.endsWith(".class") && !fileName.endsWith(".chasm")) {
								// Only copy class files, since those files are the only files modified by chasm
								// (and chasm files, since they are read by chasm)
								System.out.println("Skipping " + path);
								return;
							}
						}
						Path sub = modSrc.relativize(path);
						Path dst = modDst.resolve(sub.toString().replace(modSrc.getFileSystem().getSeparator(), modDst.getFileSystem().getSeparator()));
						try {
							FasterFiles.createDirectories(dst.getParent());
							if (COPY_ON_WRITE) {
								ExtendedFiles.mount(path, dst, MountOption.COPY_ON_WRITE);
							} else {
								FasterFiles.copy(path, dst);
							}
						} catch (IOException e) {
							throw new Error(e);
						}
					});
				} catch (IOException io) {
					throw new Error(io);
				}
			}
		}

		if (modsToRemap.isEmpty()) {
			return;
		}

		QuiltLauncher launcher = QuiltLauncherBase.getLauncher();

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(launcher.getMappingConfiguration().getMappings(), "intermediary", launcher.getTargetNamespace()))
				.renameInvalidLocals(false)
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
				remapper.readInputsAsync(tag, info.inputPath);
			}

			//Done in a 2nd loop as we need to make sure all the inputs are present before remapping
			for (ModLoadOption mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				info.outputPath = cache.resolve("/" + mod.id());
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				outputConsumer.addNonClassFiles(mod.resourceRoot(), NonClassCopyMode.FIX_META_INF, remapper);

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
		AccessWidenerRemapper remappingDecorator = new AccessWidenerRemapper(writer, remapper, "intermediary", "named");
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

	private static class RemapInfo {
		InputTag tag;
		Path inputPath;
		Path outputPath;
		OutputConsumerPath outputConsumerPath;
		Map<String, byte[]> accessWideners;
	}
}
