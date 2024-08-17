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

package org.quiltmc.loader.impl.patch.reflections;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.quiltmc.loader.api.FasterFileSystem;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ReflectionsClassPatcher {

	private static final String REF_PATCH_UTIL = Type.getInternalName(ReflectionsPatchUtils.class);

	private static final String INPUT_CLASS = "/org/quiltmc/loader/impl/patch/reflections/";

	/** Quilt loader impl package to place the newly generated class in. This uses the dot separator '.' */
	private final String patchPackage;

	/** Quilt loader impl package to place the newly generated class in. This uses the slash separator '/' */
	private final String patchInternalPackage;

	/** Target package to call into. This uses the package separator '/', and starts with text and ends with a slash,
	 * like "org/reflections/place/" */
	private final String targetPackage;

	private final Remapper mappings;

	public static void load(Map<String, byte[]> patchedClasses) {

		List<ReflectionsClassPatcher> targets = new ArrayList<>();

		targets.add(new ReflectionsClassPatcher("", "org/reflections/"));
		targets.add(new ReflectionsClassPatcher("kubejsoffline.", "pie/ilikepiefoo/kubejsoffline/reflections/"));

		// TODO: Add system property based targets!

		for (ReflectionsClassPatcher target : targets) {
			target.patch(patchedClasses);
		}
	}

	/** @param patchPackage Sub-package to place this into. This will be prefixed with
	 *            "org.quiltmc.loader.impl.patch.PATCHED.reflections." since it must go in there. */
	private ReflectionsClassPatcher(String patchPackage, String targetPackage) {
		this.patchPackage = "org.quiltmc.loader.impl.patch.PATCHED.reflections." + patchPackage;
		this.patchInternalPackage = this.patchPackage.replace('.', '/');
		this.targetPackage = targetPackage;

		if (targetPackage.startsWith("/")) {
			throw new IllegalArgumentException(targetPackage + " starts with a '/'");
		}

		if (!targetPackage.endsWith("/")) {
			throw new IllegalArgumentException(targetPackage + " doesn't end with a '/'");
		}

		Map<String, String> map = new HashMap<>();
		String originalPackage = INPUT_CLASS.substring(1);
		String newPackage = this.patchPackage.replace('.', '/');
		map.put(originalPackage + "ReflectionsPathUrlType", newPackage + "ReflectionsPathUrlType");
		map.put(originalPackage + "ReflectionsPathDir", newPackage + "ReflectionsPathDir");
		map.put(originalPackage + "ReflectionsPathFile", newPackage + "ReflectionsPathFile");
		this.mappings = new SimpleRemapper(map);
	}

	private void patch(Map<String, byte[]> patchedClasses) {

		if (QuiltLauncherBase.getLauncher().getResourceURL("/" + targetPackage + "vfs/Vfs.class") == null) {
			return;
		}

		// TODO: Just generate the classes instead of using a remapper, since that way we can guarentee
		// that they don't cause a VM crash

		String urlType = patchPackage + "ReflectionsPathUrlType";
		String dir = patchPackage + "ReflectionsPathDir";
		String file = patchPackage + "ReflectionsPathFile";
		genUrlType(urlType, patchedClasses);
		genDir(dir, patchedClasses);
		genFile(file, patchedClasses);

		try {
			ClassLoader targetClassLoader = QuiltLauncherBase.getLauncher().getTargetClassLoader();
			Class<?> urlTypeCls = Class.forName(urlType, true, targetClassLoader);
			Class.forName(dir, true, targetClassLoader);
			Class.forName(file, true, targetClassLoader);
			Class<?> vfs = Class.forName(targetPackage.replace('/', '.') + "vfs.Vfs", false, targetClassLoader);
			List<Object> list = (List) vfs.getMethod("getDefaultUrlTypes").invoke(null);
			list.add(urlTypeCls.newInstance());
		} catch (ReflectiveOperationException e) {
			throw new Error(e);
		}

		Log.info(
			LogCategory.GENERAL, "Successfully patched " + targetPackage + " to be able to handle quilt file systems."
		);
	}

	private void genUrlType(String newName, Map<String, byte[]> dst) {
		ClassWriter writer = new ClassWriter(0);
		String[] itfs = { targetPackage + "vfs/Vfs$UrlType" };
		String[] exception = { "java/lang/Exception" };
		String intName = newName.replace('.', '/');
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, intName, null, Type.getInternalName(Object.class), itfs);
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			mv.visitMaxs(1, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitEnd();
		}
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "matches", "(Ljava/net/URL;)Z", null, exception);
			mv.visitCode();
			mv.visitMaxs(3, 2);
			/* url.toURI : (URL)URI Paths.get(URI) : (URI)Path path.getFileSystem() : (Path)FileSystem instanceof
			 * FasterFileSystem */
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			mv.visitMethodInsn(
				Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/net/URI;)Ljava/nio/file/Path;", false
			);
			mv.visitMethodInsn(
				Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "getFileSystem", "()Ljava/nio/file/FileSystem;", true
			);
			mv.visitTypeInsn(Opcodes.INSTANCEOF, Type.getInternalName(FasterFileSystem.class));
			mv.visitInsn(Opcodes.IRETURN);
			mv.visitEnd();
		}
		{
			String desc = "(Ljava/net/URL;)L" + targetPackage + "vfs/Vfs$Dir;";
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "createDir", desc, null, exception);
			mv.visitCode();
			String dir = patchInternalPackage + "ReflectionsPathDir";
			mv.visitTypeInsn(Opcodes.NEW, dir);
			mv.visitInsn(Opcodes.DUP);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			mv.visitMethodInsn(
				Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get", "(Ljava/net/URI;)Ljava/nio/file/Path;", false
			);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, dir, "<init>", "(Ljava/nio/file/Path;)V", false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(3, 2);
			mv.visitEnd();
		}
		writer.visitEnd();
		dst.put(newName, writer.toByteArray());
	}

	private void genDir(String newName, Map<String, byte[]> dst) {
		ClassWriter writer = new ClassWriter(0);
		String dirInterface = Type.getInternalName(ReflectionsDir.class);
		String[] itfs = { targetPackage + "vfs/Vfs$Dir", dirInterface };
		String[] exception = { "java/lang/Exception" };
		String pathDesc = Type.getDescriptor(Path.class);
		String intName = newName.replace('.', '/');
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, intName, null, Type.getInternalName(Object.class), itfs);
		writer.visitField(Opcodes.ACC_FINAL, "path", pathDesc, null, null);
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/nio/file/Path;)V", null, null);
			mv.visitCode();
			mv.visitMaxs(2, 2);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.PUTFIELD, intName, "path", pathDesc);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitEnd();
		}
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "getPath", "()Ljava/lang/String;", null, null);
			mv.visitCode();
			mv.visitMaxs(1, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "path", pathDesc);
			mv.visitMethodInsn(
				Opcodes.INVOKESTATIC, REF_PATCH_UTIL, "dir_getPath", "(Ljava/nio/file/Path;)Ljava/lang/String;", false
			);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitEnd();
		}
		{
			// public Iterable getFiles() {
			// return ReflectionsPatchUtils.dir_getFiles(this, this.path);
			// }
			MethodVisitor mv = writer.visitMethod(
				Opcodes.ACC_PUBLIC, "getFiles", "()Ljava/lang/Iterable;", null, exception
			);
			mv.visitCode();
			String dir = patchInternalPackage + "ReflectionsPathDir";
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitTypeInsn(Opcodes.CHECKCAST, dirInterface);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "path", pathDesc);
			String desc = "(L" + dirInterface + ";Ljava/nio/file/Path;)Ljava/lang/Iterable;";
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, REF_PATCH_UTIL, "dir_getFiles", desc, false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(2, 2);
			mv.visitEnd();
		}
		{
			// public Object createFile(Path p) {
			// return new ReflectionsPathFile(this, p);
			// }
			MethodVisitor mv = writer.visitMethod(
				Opcodes.ACC_PUBLIC, "createFile", "(Ljava/nio/file/Path;)Ljava/lang/Object;", null, null
			);
			mv.visitCode();
			String file = patchInternalPackage + "ReflectionsPathFile";
			mv.visitTypeInsn(Opcodes.NEW, file);
			mv.visitInsn(Opcodes.DUP);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			String ctorDesc = "(L" + patchInternalPackage + "ReflectionsPathDir;Ljava/nio/file/Path;)V";
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, file, "<init>", ctorDesc, false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(4, 2);
			mv.visitEnd();
		}
		writer.visitEnd();
		dst.put(newName, writer.toByteArray());
	}

	private void genFile(String newName, Map<String, byte[]> dst) {
		ClassWriter writer = new ClassWriter(0);
		String[] itfs = { targetPackage + "vfs/Vfs$File" };
		String[] exception = { "java/lang/Exception" };
		String dir = "L"+patchInternalPackage + "ReflectionsPathDir;";
		String intName = newName.replace('.', '/');
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, intName, null, Type.getInternalName(Object.class), itfs);
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "dir", dir, null, null);
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "path", Type.getDescriptor(Path.class), null, null);
		{
			String ctorDesc = "(" + dir + "Ljava/nio/file/Path;)V";
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDesc, null, null);
			mv.visitCode();
			mv.visitMaxs(2, 3);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.PUTFIELD, intName, "dir", dir);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitFieldInsn(Opcodes.PUTFIELD, intName, "path", "Ljava/nio/file/Path;");
			mv.visitInsn(Opcodes.RETURN);
			mv.visitEnd();
		}
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "path", "Ljava/nio/file/Path;");
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "getFileName", "()Ljava/nio/file/Path;", true);
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "toString", "()Ljava/lang/String;", true);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(2, 1);
			mv.visitEnd();
		}
		{
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "getRelativePath", "()Ljava/lang/String;", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "dir", dir);
			mv.visitFieldInsn(Opcodes.GETFIELD, dir.substring(1, dir.length() - 1), "path", "Ljava/nio/file/Path;");
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "path", "Ljava/nio/file/Path;");
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, REF_PATCH_UTIL, "file_getRelativePath", "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/lang/String;", false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(3, 1);
			mv.visitEnd();
		}
		{
			String[] io = { Type.getInternalName(IOException.class) };
			MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "openInputStream", "()Ljava/io/InputStream;", null, io);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, intName, "path", "Ljava/nio/file/Path;");
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, REF_PATCH_UTIL, "file_openInputStream", "(Ljava/nio/file/Path;)Ljava/io/InputStream;", false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(2, 1);
			mv.visitEnd();
		}
		writer.visitEnd();
		dst.put(newName, writer.toByteArray());
	}
}
