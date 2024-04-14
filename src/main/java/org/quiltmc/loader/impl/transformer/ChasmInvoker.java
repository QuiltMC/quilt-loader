/*
 * Copyright 2022, 2023 QuiltMC
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.quiltmc.chasm.api.ChasmProcessor;
import org.quiltmc.chasm.api.ClassResult;
import org.quiltmc.chasm.api.Transformer;
import org.quiltmc.chasm.api.util.ClassInfo;
import org.quiltmc.chasm.api.util.Context;
import org.quiltmc.chasm.internal.transformer.ChasmLangTransformer;
import org.quiltmc.chasm.lang.api.ast.Node;
import org.quiltmc.chasm.lang.api.metadata.Metadata;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LArray;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ChasmInvoker {

	static void applyChasm(TransformCache cache)
		throws ChasmTransformException {
		try {
			applyChasm0(cache);
		} catch (Throwable e) {
			throw new ChasmTransformException("Failed to apply chasm!", e);
		}
	}

	static void applyChasm0(TransformCache cache) throws IOException {
		Map<String, ModLoadOption> package2mod = new HashMap<>();
		Map<String, byte[]> inputClassCache = new HashMap<>();

		// TODO: Move chasm searching to here!
		for (ModLoadOption mod : cache.getModsInCache()) {
			Path path2 = cache.getRoot(mod);
			if (!FasterFiles.isDirectory(path2)) {
				continue;
			}
			Files.walkFileTree(path2, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						package2mod.put(path2.relativize(file.getParent()).toString(), mod);
						inputClassCache.put(path2.relativize(file).toString(), Files.readAllBytes(file));
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		ChasmProcessor chasm = new ChasmProcessor(new Context() {

			@Override
			public @Nullable ClassInfo getClassInfo(String className) {
				byte[] bytes = readFile(LoaderUtil.getClassFileName(className));
				if (bytes == null) {
					return null;
				}
				return ClassInfo.fromBytes(bytes);
			}

			@Override
			public byte @Nullable [] readFile(String path) {
				try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
					return stream != null ? FileUtil.readAllBytes(stream) : null;
				} catch (IOException e) {
					// TODO: Is this correct? Chasm probably won't be expecting this
					throw new UncheckedIOException(e);
				}
			}
		});

		for (ModLoadOption mod : cache.getModsInCache()) {
			Path modPath = cache.getRoot(mod);
			if (!FasterFiles.exists(modPath)) {
				continue;
			}

			// QMJ spec: "experimental_chasm_transformers"
			// either a string, or a list of strings
			// each string is a folder which will be recursively searched for chasm transformers.
			// TODO: duplicated in QuiltTransformers
			LoaderValue value = mod.metadata().value("experimental_chasm_transformers");

			final String[] paths;
			if (value == null) {
				paths = new String[0];
			} else if (value.type() == LType.STRING) {
				paths = new String[] { value.asString() };
			} else if (value.type() == LType.ARRAY) {
				LArray array = value.asArray();
				paths = new String[array.size()];
				for (int i = 0; i < array.size(); i++) {
					LoaderValue entry = array.get(i);
					if (entry.type() == LType.STRING) {
						paths[i] = entry.asString();
					} else {
						Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers[" + i + "]' in " + mod.id());
					}
				}
			} else {
				paths = new String[0];
				Log.warn(LogCategory.CHASM, "Unknown value found for 'experimental_chasm_transformers' in " + mod.id());
			}

			List<Path> chasmRoots = new ArrayList<>();

			for (String path : paths) {
				if (path == null) {
					continue;
				}
				try {
					chasmRoots.add(modPath.resolve(path.replace("/", modPath.getFileSystem().getSeparator())));
				} catch (InvalidPathException e) {
					Log.warn(LogCategory.CHASM, "Invalid path '" + path + "' for 'experimental_chasm_transformers' in " + mod.id());
				}
			}

			Files.walkFileTree(modPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						byte[] bytes = Files.readAllBytes(file);
						Metadata meta = new Metadata();
						meta.put(QuiltMetadata.class, new QuiltMetadata(mod, LoaderUtil.getClassNameFromTransformCache(file.toString())));
						chasm.addClass(bytes, meta);
					} else if (file.getFileName().toString().endsWith(".chasm")) {
						for (Path chasmRoot : chasmRoots) {
							if (file.startsWith(chasmRoot)) {
								String chasmId = mod.id() + ":" + chasmRoot.relativize(file).toString();
								chasmId = chasmId.replace(chasmRoot.getFileSystem().getSeparator(), "/");
								if (chasmId.endsWith(".chasm")) {
									chasmId = chasmId.substring(0, chasmId.length() - ".chasm".length());
								}
								Log.info(LogCategory.CHASM, "Found chasm transformer: '" + chasmId + "'");
								Node node = Node.parse(file);
								Transformer transformer = new ChasmLangTransformer(chasmId, node, chasm.getContext());
								chasm.addTransformer(transformer);
								break;
							}
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		List<ClassResult> classResults = chasm.process();
		for (ClassResult result : classResults) {
			switch (result.getType()) {
				case ADDED:
				case MODIFIED: {
					QuiltMetadata qm = this_value_is_actually_nullable(result.getMetadata().get(QuiltMetadata.class));
					byte[] bytes = result.getClassBytes();
					ClassReader cr = new ClassReader(bytes);
					String className = cr.getClassName();
					final Path rootTo;
					if (qm != null) {
						rootTo = cache.getRoot(qm.from);
					} else {
						ModLoadOption mod = package2mod.get(className.substring(0, className.lastIndexOf('/')));
						if (mod == null) {
							throw new AbstractMethodError("// TODO: Support classloading from unknown mods!");
						}
						rootTo = cache.getRoot(mod);
					}
					Path to = rootTo.resolve(LoaderUtil.getClassFileName(className));
					Files.createDirectories(to.getParent());
					Files.write(to, bytes);
					break;
				}
				case UNMODIFIED: {
					break;
				}
				case REMOVED: {
					QuiltMetadata qm = this_value_is_actually_nullable(result.getMetadata().get(QuiltMetadata.class));
					if (qm != null) {
						cache.hideClass(LoaderUtil.getClassNameFromTransformCache(qm.name), "it was removed by a chasm transformer");
					} else {
						throw new UnsupportedOperationException("Cannot remove unknown class");
					}
					break;
				}
				default: {
					throw new UnsupportedChasmException(
						"Chasm returned an unknown 'ClassResult.getType()': ''" + result.getType()
							+ "' - you might need to update loader?"
					);
				}
			}
		}
	}

	@Nullable
	private static <T> T this_value_is_actually_nullable(T in) {
		// Eclipse thinks that "T.class" is "Class<@NotNull T>", which is wrong
		return in;
	}

	static class QuiltMetadata {

		final ModLoadOption from;
		// the dot name of this class
		final String name;

		public QuiltMetadata(ModLoadOption from, String name) {
			this.from = from;
			this.name = name;
		}
	}
}
