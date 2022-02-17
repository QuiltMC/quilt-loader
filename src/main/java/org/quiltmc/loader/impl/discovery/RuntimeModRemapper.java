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

package org.quiltmc.loader.impl.discovery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.commons.Remapper;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.mappings.TinyRemapperMappingsHelper;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public final class RuntimeModRemapper {

	public static void remap(List<ModCandidate> modCandidates, FileSystem fileSystem) {
		List<ModCandidate> modsToRemap = modCandidates.stream()
				.filter(ModCandidate::requiresRemap)
				.collect(Collectors.toList());

		if (modsToRemap.isEmpty()) {
			return;
		}

		List<ModCandidate> modsToSkip = modCandidates.stream()
				.filter(mc -> !mc.requiresRemap())
				.collect(Collectors.toList());

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

		List<ModCandidate> remappedMods = new ArrayList<>();

		try {
			Map<ModCandidate, RemapInfo> infoMap = new HashMap<>();

			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = new RemapInfo();
				infoMap.put(mod, info);

				InputTag tag = remapper.createInputTag();
				info.tag = tag;
				info.inputPath = mod.getOriginPath().toAbsolutePath();
				remapper.readInputsAsync(tag, info.inputPath);
			}

			//Done in a 2nd loop as we need to make sure all the inputs are present before remapping
			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				info.outputPath = fileSystem.getPath(info.inputPath.getFileName().toString() + "-" + UUID.randomUUID() + "-remappedOutput.jar");
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(info.inputPath, false);

				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + info.inputPath.getFileName() + " for NIO reading!");
				}

				Path inputJar = delegate.get().getRootDirectories().iterator().next();
				outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);

				info.outputConsumerPath = outputConsumer;

				remapper.apply(outputConsumer, info.tag);
			}

			//Done in a 3rd loop as this can happen when the remapper is doing its thing.
			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				if (!mod.getMetadata().accessWideners().isEmpty()) {
					info.accessWideners = new HashMap<>();
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.inputPath, false)) {
						FileSystem fs = jarFs.get();
						for (String accessWidener : mod.getMetadata().accessWideners()) {
							info.accessWideners.put(accessWidener, remapAccessWidener(Files.readAllBytes(fs.getPath(accessWidener)), remapper.getRemapper()));
						}
					} catch (Throwable t) {
						throw new RuntimeException("Error remapping access widener for mod '"+mod.getId()+"'!", t);
					}
				}
			}

			remapper.finish();

			for (ModCandidate mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);

				info.outputConsumerPath.close();

				if (info.accessWideners != null) {
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.outputPath, false)) {
						FileSystem fs = jarFs.get();
						for (Map.Entry<String, byte[]> entry : info.accessWideners.entrySet()) {
							Files.delete(fs.getPath(entry.getKey()));
							Files.write(fs.getPath(entry.getKey()), entry.getValue());
						}
					}
				}
				// TODO: intentional leak?
				FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.outputPath, false);
				remappedMods.add(new ModCandidate(mod.getOriginPath(), jarFs.get().getPath("/"), mod.getInfo(), 0, false));
			}

		} catch (IOException e) {
			remapper.finish();
			throw new RuntimeException("Failed to remap mods", e);
		}
		// TODO: erases order
		modCandidates.clear();
		modCandidates.addAll(remappedMods);
		modCandidates.addAll(modsToSkip);
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
