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
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.patch.PatchLoader;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ReflectionsClassPatcher extends PatchLoader {

	static final String INPUT_CLASS = "/org/quiltmc/loader/impl/patch/reflections/";
	static final String TARGET_PACKAGE = "org.quiltmc.loader.impl.patch.reflections.";

	public static void load(Map<String, byte[]> patchedClasses) {
		if (!QuiltLoader.isModLoaded("org_reflections_reflections")) {
			return;
		}

		String urlType = TARGET_PACKAGE + "ReflectionsPathUrlType";
		try {
			patchedClasses.put(urlType, patchUrlType());
			patchedClasses.put(TARGET_PACKAGE + "ReflectionsPathDir", patchDir());
			patchedClasses.put(TARGET_PACKAGE + "ReflectionsPathFile", patchFile());
		} catch (IOException e) {
			throw new Error("Failed to patch a reflections class!", e);
		}

		try {
			Class.forName(urlType, true, QuiltLauncherBase.getLauncher().getTargetClassLoader());
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		}

		Log.info(LogCategory.GENERAL, "Successfully patched reflections to be able to handle quilt file systems.");
	}

	private static byte[] patchUrlType() throws IOException {
		byte[] input = FileUtil.readAllBytes(
			ReflectionsClassPatcher.class.getResourceAsStream(INPUT_CLASS + "ReflectionsPathUrlType.class")
		);
		ClassReader reader = new ClassReader(input);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(QuiltLoaderImpl.ASM_VERSION, writer) {
			String owner;

			@Override
			public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
				super.visit(
					version, Opcodes.ACC_PUBLIC | access, owner = name, signature, superName, new String[] {
						"org/reflections/vfs/Vfs$UrlType" }
				);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
				String[] exceptions) {
				if (name.equals("createDir")) {
					String dirType = "org/reflections/vfs/Vfs$Dir";
					MethodVisitor delegate = super.visitMethod(
						access, name, "(Ljava/net/URL;)L" + dirType + ";", null, exceptions
					);
					delegate.visitMaxs(2, 2);
					delegate.visitCode();
					delegate.visitVarInsn(Opcodes.ALOAD, 0);
					delegate.visitVarInsn(Opcodes.ALOAD, 1);
					delegate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false);
					delegate.visitTypeInsn(Opcodes.CHECKCAST, dirType);
					delegate.visitInsn(Opcodes.ARETURN);
					delegate.visitEnd();
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, 0);
		return writer.toByteArray();
	}

	private static byte[] patchDir() throws IOException {
		byte[] input = FileUtil.readAllBytes(
			ReflectionsClassPatcher.class.getResourceAsStream(INPUT_CLASS + "ReflectionsPathDir.class")
		);
		ClassReader reader = new ClassReader(input);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(QuiltLoaderImpl.ASM_VERSION, writer) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
				super.visit(
					version, Opcodes.ACC_PUBLIC | access, name, signature, superName, new String[] {
						"org/reflections/vfs/Vfs$Dir" }
				);
			}
		}, 0);
		return writer.toByteArray();
	}

	private static byte[] patchFile() throws IOException {
		byte[] input = FileUtil.readAllBytes(
			ReflectionsClassPatcher.class.getResourceAsStream(INPUT_CLASS + "ReflectionsPathFile.class")
		);
		ClassReader reader = new ClassReader(input);
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(QuiltLoaderImpl.ASM_VERSION, writer) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
				super.visit(
					version, Opcodes.ACC_PUBLIC | access, name, signature, superName, new String[] {
						"org/reflections/vfs/Vfs$File" }
				);
			}
		}, 0);
		return writer.toByteArray();
	}
}
