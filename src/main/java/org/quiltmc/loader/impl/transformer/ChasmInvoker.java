package org.quiltmc.loader.impl.transformer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.quiltmc.chasm.api.ChasmProcessor;
import org.quiltmc.chasm.api.ClassData;
import org.quiltmc.chasm.api.Transformer;
import org.quiltmc.chasm.api.util.Context;
import org.quiltmc.chasm.internal.transformer.ChasmLangTransformer;
import org.quiltmc.chasm.lang.api.ast.Node;
import org.quiltmc.chasm.lang.api.metadata.Metadata;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModResolutionException;

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
			if (!Files.isDirectory(path2)) {
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
					if (Files.isRegularFile(path2)) {
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
				// TODO: Read both the class bytes, and somehow get classes from jdk libraries?
				// TODO Auto-generated method stub
				throw new AbstractMethodError("// TODO: Implement this!");
			}

			@Override
			public boolean isAssignable(String leftClass, String rightClass) {
				// TODO Auto-generated method stub
				throw new AbstractMethodError("// TODO: Implement this!");
			}

			@Override
			public String getSuperClass(String className) {
				// TODO Auto-generated method stub
				throw new AbstractMethodError("// TODO: Implement this!");
			}
		});

		for (ModLoadOption mod : modList) {
			Path modPath = root.resolve(mod.id());
			if (!Files.exists(modPath)) {
				continue;
			}
			Path chasmRoot = modPath.resolve("chasm_transformers");
			Files.walkFileTree(modPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						byte[] bytes = Files.readAllBytes(file);
						Metadata meta = new Metadata();
						meta.put(QuiltMetadata.class, new QuiltMetadata(mod));
						chasm.addClass(new ClassData(bytes, meta));
					} else if (file.startsWith(chasmRoot) && file.getFileName().toString().endsWith(".chasm")) {
						String chasmId = mod.id() + ":" + chasmRoot.relativize(chasmRoot).toString();
						Node node = Node.parse(file);
						Transformer transformer = new ChasmLangTransformer(chasmId, node, chasm.getContext());
						chasm.addTransformer(transformer);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}

		List<ClassData> changed = chasm.process(true);
		for (ClassData changedTo : changed) {

			ClassReader cr = new ClassReader(changedTo.getClassBytes());
			ClassNameGetter namer = new ClassNameGetter();
			cr.accept(namer, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			QuiltMetadata qm = changedTo.getMetadata().get(QuiltMetadata.class);
			final Path rootTo;
			if (qm != null) {
				rootTo = root.resolve(qm.from.id());
			} else {
				// TODO: Infer location from package structure!
				// TODO: Classload from this place too!
				rootTo = root.resolve("misc-classes");
			}
			Path to = rootTo.resolve(namer.name + ".class");
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

	static class ClassNameGetter extends ClassVisitor {

		String name;

		protected ClassNameGetter() {
			super(QuiltLoaderImpl.ASM_VERSION);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
			String[] interfaces) {

			this.name = name;
		}

	}
}
