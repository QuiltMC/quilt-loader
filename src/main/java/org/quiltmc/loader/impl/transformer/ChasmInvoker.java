/*
 * Copyright 2022 QuiltMC
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
import org.objectweb.asm.Opcodes;
import org.quiltmc.chasm.api.ChasmProcessor;
import org.quiltmc.chasm.api.ClassData;
import org.quiltmc.chasm.api.Transformer;
import org.quiltmc.chasm.api.util.Context;
import org.quiltmc.chasm.internal.transformer.ChasmLangTransformer;
import org.quiltmc.chasm.lang.api.ast.Node;
import org.quiltmc.chasm.lang.api.metadata.Metadata;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LArray;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

class ChasmInvoker {

	static void applyChasm(Path root, List<ModLoadOption> modList, ModSolveResult result)
		throws ModResolutionException {
		try {
			applyChasm0(root, modList, result);
		} catch (IOException e) {
			throw new ModResolutionException("Failed to apply chasm!", e);
		}
	}

	static void applyChasm0(Path root, List<ModLoadOption> modList, ModSolveResult result) throws IOException {
		Map<String, String> package2mod = new HashMap<>();
		Map<String, byte[]> inputClassCache = new HashMap<>();

		// TODO: Move chasm searching to here!
		for (ModLoadOption mod : modList) {
			Path path2 = root.resolve(mod.id());
			if (!FasterFiles.isDirectory(path2)) {
				continue;
			}
			Files.walkFileTree(path2, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						package2mod.put(path2.relativize(file.getParent()).toString(), mod.id());
						inputClassCache.put(path2.relativize(file).toString(), Files.readAllBytes(file));
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		ChasmProcessor chasm = new ChasmProcessor(new Context() {

			@Override
			public byte @Nullable [] readFile(String path) {

				byte[] cached = inputClassCache.get(path);
				if (cached != null) {
					return cached;
				}

				// Chasm wants this to be pure, which it is!
				// (since the transform cache isn't modified during chasm invocation anyway)

				for (ModLoadOption mod : modList) {
					Path path2 = root.resolve(mod.id()).resolve(path);
					if (FasterFiles.isRegularFile(path2)) {
						try {
							return Files.readAllBytes(path2);
						} catch (IOException e) {
							return null;
						}
					}
				}
				return null;
			}

			@Override
			public boolean isInterface(String className) {
				ClassReader cr = new ClassReader(readFile(LoaderUtil.getClassFileName(className)));
				return (Opcodes.ACC_INTERFACE & cr.getAccess()) != 0;
			}

			@Override
			public boolean isAssignable(String leftClass, String rightClass) {
				if ("java/lang/Object".equals(leftClass)) {
					return true;
				}

				byte[] file = readFile(LoaderUtil.getClassFileName(rightClass));
				if (file == null) {
					return false;
				}

				ClassReader cr = new ClassReader(file);

				if (cr.getSuperName().equals(leftClass)) {
					return true;
				}

				for (String itf : cr.getInterfaces()) {
					if (itf.equals(leftClass)) {
						return true;
					}
				}

				if (isAssignable(leftClass, cr.getSuperName())) {
					return true;
				}

				for (String itf : cr.getInterfaces()) {
					if (isAssignable(leftClass, itf)) {
						return true;
					}
				}

				return false;
			}

			@Override
			public String getSuperClass(String className) {
				return new ClassReader(readFile(LoaderUtil.getClassFileName(className))).getSuperName();
			}
		});

		for (ModLoadOption mod : modList) {
			Path modPath = root.resolve(mod.id());
			if (!FasterFiles.exists(modPath)) {
				continue;
			}

			// QMJ spec: "experimental_chasm_transformers"
			// either a string, or a list of strings
			// each string is a folder which will be recursively searched for chasm transformers.
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
					chasmRoots.add(modPath.resolve(path));
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
						meta.put(QuiltMetadata.class, new QuiltMetadata(mod));
						chasm.addClass(new ClassData(bytes, meta));
					} else if (file.getFileName().toString().endsWith(".chasm")) {
						for (Path chasmRoot : chasmRoots) {
							if (file.startsWith(chasmRoot)) {
								String chasmId = mod.id() + ":" + chasmRoot.relativize(file).toString();
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

		List<ClassData> changed = chasm.process(true);
		for (ClassData changedTo : changed) {

			ClassReader cr = new ClassReader(changedTo.getClassBytes());

			QuiltMetadata qm = changedTo.getMetadata().get(QuiltMetadata.class);
			final Path rootTo;
			if (qm != null) {
				rootTo = root.resolve(qm.from.id());
			} else {
				String mod = package2mod.get(cr.getClassName().substring(0, cr.getClassName().lastIndexOf('/')));
				if (mod == null) {
					// TODO: Classload from this place!
					mod = "misc-classes";
				}
				rootTo = root.resolve(mod);
			}
			Path to = rootTo.resolve(cr.getClassName() + ".class");
			Files.createDirectories(to.getParent());
			Files.write(to, changedTo.getClassBytes());
		}
	}

	static class QuiltMetadata {

		final ModLoadOption from;

		public QuiltMetadata(ModLoadOption from) {
			this.from = from;
		}
	}
}
