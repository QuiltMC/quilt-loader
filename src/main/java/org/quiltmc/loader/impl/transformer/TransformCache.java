/*
 * Copyright 2023 QuiltMC
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.ParseException;

/**
 * A representation of the transform cache to be used by transformers when generating the cache for the first time.
 */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class TransformCache {
	private final Path root;
	private final Map<ModLoadOption, Path> modRoots = new HashMap<>();
	private final List<ModLoadOption> orderedMods;
	private final Set<String> hiddenClasses = new HashSet<>();
	private static final boolean COPY_ON_WRITE = true;

	public TransformCache(Path root, List<ModLoadOption> orderedMods) {
		this.root = root;
		this.orderedMods = orderedMods.stream().filter(mod -> mod.needsTransforming() && !QuiltLoaderImpl.MOD_ID.equals(mod.id())).collect(Collectors.toList());

		for (ModLoadOption mod : this.orderedMods) {
			Path modSrc = mod.createTransformRoot();
			Path modDst = root.resolve(mod.id());
			modRoots.put(mod, modDst);

			final boolean onlyTransformableFiles = mod.couldResourcesChange();

			try {
				if (onlyTransformableFiles) {
					// note: we could provide a folder to pass in more data (e.g. a /transformers dir with metadata)
					// we could also provide meta-inf here:
//					copyFile(modSrc.resolve("META-INF/MANIFEST.MF"), modSrc, modDst);

					// Copy mixin + AWs over
					for (String mixin : mod.metadata().mixins(QuiltLauncherBase.getLauncher().getEnvironmentType())) {
						copyFile(modSrc.resolve(mixin), modSrc, modDst);
						// find the refmap and copy it too
						String refmap = extractRefmap(modSrc.resolve(mixin));
						if (refmap != null) {
							// multiple mixins can reference the same refmap
							copyFile(modSrc.resolve(refmap), modSrc, modDst, StandardCopyOption.REPLACE_EXISTING);

						}
					}
					for (String aw : mod.metadata().accessWideners()) {
						copyFile(modSrc.resolve(aw), modSrc, modDst);
					}

					LoaderValue value = mod.metadata().value("experimental_chasm_transformers");

					// TODO: copied from ChasmInvoker
					final String[] chasmPaths;
					if (value == null) {
						chasmPaths = new String[0];
					} else if (value.type() == LoaderValue.LType.STRING) {
						chasmPaths = new String[]{value.asString()};
					} else if (value.type() == LoaderValue.LType.ARRAY) {
						LoaderValue.LArray array = value.asArray();
						chasmPaths = new String[array.size()];
						for (int i = 0; i < array.size(); i++) {
							LoaderValue entry = array.get(i);
							if (entry.type() == LoaderValue.LType.STRING) {
								chasmPaths[i] = entry.asString();
							} else {
								Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers[" + i + "]' in " + mod.id());
							}
						}
					} else {
						chasmPaths = new String[0];
						Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers' in " + mod.id());
					}

					for (String chasmPath : chasmPaths) {
						copyFile(modSrc.resolve(chasmPath), modSrc, modDst);
					}

					// copy classes for mods which don't need remapped
					if (mod.namespaceMappingFrom() == null) {
						try (Stream<Path> stream = Files.walk(modSrc)) {
							stream
								.filter(FasterFiles::isRegularFile)
								.filter(p -> p.getFileName().toString().endsWith(".class") || p.getFileName().toString().endsWith(".chasm"))
								.forEach(path -> copyFile(path, modSrc, modDst));
						}
					}
				} else if (mod.namespaceMappingFrom() != null) {
					// Copy everything that isn't a class file, since those get remapped
					try (Stream<Path> stream = Files.walk(modSrc)) {
						stream
							.filter(FasterFiles::isRegularFile)
							.filter(p -> !p.getFileName().toString().endsWith(".class"))
							.forEach(path -> copyFile(path, modSrc, modDst));
					}
				} else {
					// Copy everything
					try (Stream<Path> stream = Files.walk(modSrc)) {
						stream
							.filter(FasterFiles::isRegularFile)
							.forEach(path -> copyFile(path, modSrc, modDst));
					}
				}
			} catch (IOException io) {
				throw new UncheckedIOException(io);
			}
		}
		// Populate mods that need remapped
		RuntimeModRemapper.remap(this);
		for (ModLoadOption orderedMod : this.orderedMods) {
			modRoots.put(orderedMod, root.resolve(orderedMod.id() + "/"));
		}
	}

	public Path getRoot(ModLoadOption mod) {
		return modRoots.get(mod);
	}

	public List<ModLoadOption> getMods() {
		return Collections.unmodifiableList(orderedMods);
	}

	public Set<String> getHiddenClasses() {
		return Collections.unmodifiableSet(hiddenClasses);
	}

	public void forEachClassFile(ClassConsumer action)
			throws IOException {
		for (ModLoadOption mod : orderedMods) {
			visitFolder(mod, getRoot(mod), action);
		}
	}

	public void hideClass(String className) {
		hiddenClasses.add(className);
	}

	private static void copyFile(Path path, Path modSrc, Path modDst, CopyOption... copyOptions) {
		if (!FasterFiles.exists(path)) {
			return;
		}
		Path sub = modSrc.relativize(path);
		Path dst = modDst.resolve(sub.toString().replace(modSrc.getFileSystem().getSeparator(), modDst.getFileSystem().getSeparator()));
		try {
			FasterFiles.createDirectories(dst.getParent());
			if (COPY_ON_WRITE) {
				ExtendedFiles.copyOnWrite(path, dst, copyOptions);
			} else {
				FasterFiles.copy(path, dst, copyOptions);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Nullable
	private static String extractRefmap(Path mixin) {
		try (JsonReader reader = JsonReader.json(mixin)) {
			// if this crashes because the structure sometimes doesn't look like this, forward complaints to glitch
			reader.beginObject();
			while (reader.hasNext()) {
				if (reader.nextName().equals("refmap")) {
					return reader.nextString();
				} else {
					reader.skipValue();
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (ParseException e) {
			throw new RuntimeException("Failed to extract the refmap from " + mixin, e);
		}

		return null;
	}

	private void visitFolder(ModLoadOption mod, Path root, ClassConsumer action) throws IOException {
		if (!Files.isDirectory(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				String folderName = Objects.toString(dir.getFileName());
				if (folderName != null && !couldBeJavaElement(folderName, false)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String fileName = file.getFileName().toString();
				if (fileName.endsWith(".class") && couldBeJavaElement(fileName, true)) {
					String name = LoaderUtil.getClassNameFromTransformCache(file.toString());
					if (!hiddenClasses.contains(name)) {
						byte[] result = action.run(mod, name, file);
						if (result != null) {
							Files.write(file, result);
						}
					}
				}
				return FileVisitResult.CONTINUE;
			}

			private boolean couldBeJavaElement(String name, boolean ignoreClassSuffix) {
				int end = name.length();
				if (ignoreClassSuffix) {
					end -= ".class".length();
				}
				for (int i = 0; i < end; i++) {
					if (name.charAt(i) == '.') {
						return false;
					}
				}
				return true;
			}
		});
	}

	@FunctionalInterface
	public interface ClassConsumer {
		/**
		 * Consume a class and potentially transform it.
		 *
		 * @param mod       the mod which "owns" this class file
		 * @param className the name of the class in dot form (e.g. {@code net.minecraft.client.MinecraftClient$1}
		 * @return the transformed bytes, or null if nothing was changed
		 */
		byte @Nullable [] run(ModLoadOption mod, String className, Path file) throws IOException;
	}
}
