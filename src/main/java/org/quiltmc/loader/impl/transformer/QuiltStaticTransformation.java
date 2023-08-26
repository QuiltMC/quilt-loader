package org.quiltmc.loader.impl.transformer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.filesystem.QuiltMapFileSystem;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.parsers.json.JsonReader;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
final class QuiltStaticTransformation {
	private static final boolean COPY_ON_WRITE = true;

	static void populateAndRun(Path root, List<ModLoadOption> modList) throws ModResolutionException, IOException {
		Map<ModLoadOption, Path> modRoots = createRoots(root, modList);
		QuiltMapFileSystem.dumpEntries(root.getFileSystem(), "after-copy");

		// Transform time!
		// Load AWs
		AccessWidener accessWidener = loadAccessWideners(modRoots);
		// game provider transformer and QuiltTransformer
		forEachClassFile(modRoots, (mod, file) -> {
			String name = file.toString();
			name = name.substring(name.indexOf('/', 1) + 1); // remove mod id
			name = name.replace('/', '.');
			name = name.substring(0, name.length() - 6); // remove .class
			byte[] classBytes = QuiltLauncherBase.getLauncher().getEntrypointTransformer().transform(name);

			if (classBytes == null) {
				classBytes = Files.readAllBytes(file);
			}

			return QuiltTransformer.transform(
					QuiltLoader.isDevelopmentEnvironment(),
					QuiltLauncherBase.getLauncher().getEnvironmentType(),
					accessWidener,
					name,
					classBytes
			);
		});

		// chasm
		if (Boolean.getBoolean(SystemProperties.ENABLE_EXPERIMENTAL_CHASM)) {
			ChasmInvoker.applyChasm(modRoots);
		}
		InternalsHiderTransform internalsHider = new InternalsHiderTransform(InternalsHiderTransform.Target.MOD);
		Map<Path, ModLoadOption> classes = new HashMap<>();

		// internals hider
		// the double read is necessary to avoid storing all classes in memory at once, and thus having memory complexity
		// proportional to mod count
		forEachClassFile(modRoots, (mod, file) -> {
			byte[] classBytes = Files.readAllBytes(file);
			// KnotClassDelegate.getRawClassByteArray hardcodes an empty byte array to be the same thing as the class not existing.
			// TODO: support deleting classes without a hack in knot
			if (classBytes.length == 0) {
				return null;
			}
			classes.put(file, mod);
			internalsHider.scanClass(mod, file, classBytes);
			return null;
		});

		for (Map.Entry<Path, ModLoadOption> entry : classes.entrySet()) {
			byte[] classBytes = Files.readAllBytes(entry.getKey());
			// KnotClassDelegate.getRawClassByteArray hardcodes an empty byte array to be the same thing as the class not existing.
			// TODO: support deleting classes without a hack in knot
			if (classBytes.length == 0) {
				continue;
			}
			byte[] newBytes = internalsHider.run(entry.getValue(), classBytes);
			if (newBytes != null) {
				Files.write(entry.getKey(), newBytes);
			}
		}

		internalsHider.finish();
	}

	private static AccessWidener loadAccessWideners(Map<ModLoadOption, Path> modRoots) {
		AccessWidener ret = new AccessWidener();
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(ret);

		for (Map.Entry<ModLoadOption, Path> entry : modRoots.entrySet()) {
			for (String accessWidener : entry.getKey().metadata().accessWideners()) {

				Path path = entry.getValue().resolve(accessWidener);

				if (!FasterFiles.isRegularFile(path)) {
					throw new RuntimeException("Failed to find accessWidener file from mod " + entry.getKey().metadata().id() + " '" + accessWidener + "'");
				}

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					accessWidenerReader.read(reader, QuiltLoader.getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read accessWidener file from mod " + entry.getKey().metadata().id(), e);
				}
			}
		}

		return ret;
	}

	private static void forEachClassFile(Map<ModLoadOption, Path> roots, ClassConsumer action)
			throws IOException {
		for (Map.Entry<ModLoadOption, Path> entry : roots.entrySet()) {
			visitFolder(entry.getKey(), entry.getValue(), action);
		}

//		visitFolder(null, root.resolve(TRANSFORM_CACHE_NONMOD_CLASSLOADABLE), action);
	}

	private static void visitFolder(ModLoadOption mod, Path root, ClassConsumer action) throws IOException {
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
					byte[] result = action.run(mod, file);
					if (result != null) {
						Files.write(file, result);
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
	interface ClassConsumer {
		byte[] run(ModLoadOption mod, Path file) throws IOException;
	}


	private static Map<ModLoadOption, Path> createRoots(Path root, List<ModLoadOption> modList) {
		Map<ModLoadOption, Path> modRoots = new HashMap<>();

		for (ModLoadOption mod : modList) {
			if (mod.needsTransforming() && !QuiltLoaderImpl.MOD_ID.equals(mod.id())) {
				Path modSrc = mod.resourceRoot();
				Path modDst = root.resolve(mod.id());
				modRoots.put(mod, modDst);
				try {
					// note: we could provide a folder to pass in more data (e.g. /transformers)
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
						chasmPaths = new String[] { value.asString() };
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
				} catch (IOException io) {
					throw new UncheckedIOException(io);
				}
			}
		}
		// Populate mods that need remapped
		RuntimeModRemapper.remap(modRoots);

		return modRoots;
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
        }

		return null;
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
}
