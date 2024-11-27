/*
 * Copyright 2024 QuiltMC
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

import java.lang.invoke.MethodHandles.Lookup;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class ReflectiveFixer extends ClassVisitor {

	private static final String OWNER_CLASS = Type.getInternalName(Class.class);
	private static final String OWNER_LOOKUP = Type.getInternalName(Lookup.class);

	private static final String OWNER_REFLECTIVE_UTIL = Type.getInternalName(QuiltReflectiveFixUtil.class);
	private static final String OWNER_REFLECTIVE_MTH = Type.getInternalName(
		QuiltReflectiveFixUtil.MethodIntermediateArguments.class
	);
	private static final String OWNER_REFLECTIVE_TMP = Type.getInternalName(
		QuiltReflectiveFixUtil.LookupIntermediateArguments.class
	);

	ReflectiveFixer(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
		String[] exceptions) {

		return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {

			int stackIncrease = 0;

			@Override
			public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {

				if (OWNER_CLASS.equals(owner)) {
					if ("forName".equals(name)) {

						switch (descriptor) {
							case "(Ljava/lang/String;)Ljava/lang/Class;":// (String)
							case "(Ljava/lang/Module;Ljava/lang/String;)Ljava/lang/String;": {// (Module, String)
								super.visitMethodInsn(
									Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixClassName",
									"(Ljava/lang/String;)Ljava/lang/String;", false
								);
								break;
							}
							case "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;": {
								// (String, boolean, ClassLoader)

								// A fun dance

								// [?, ?, ?, String, boolean, ClassLoader]
								super.visitInsn(Opcodes.DUP_X2);
								// [?, ?, ?, ClassLoader, String, boolean, ClassLoader]
								super.visitInsn(Opcodes.POP);
								// [?, ?, ?, ClassLoader, String, boolean]
								super.visitInsn(Opcodes.DUP_X2);
								// [?, ?, ?, boolean, ClassLoader, String, boolean]
								super.visitInsn(Opcodes.POP);
								// [?, ?, ?, boolean, ClassLoader, String]
								super.visitMethodInsn(
									Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixClassName",
									"(Ljava/lang/String;)Ljava/lang/String;", false
								);
								// [?, ?, ?, boolean, ClassLoader, String]
								super.visitInsn(Opcodes.DUP_X2);
								// [?, ?, ?, String, boolean, ClassLoader, String]
								super.visitInsn(Opcodes.POP);
								// [?, ?, ?, String, boolean, ClassLoader]
								stackIncrease = Math.max(stackIncrease, 1);
								break;
							}
							default: {
								Log.warn(
									LogCategory.GENERAL, "ReflectiveFixer: Unknown / new Class.forName descriptor: "
										+ descriptor
								);
								break;
							}
						}
					} else if ("getDeclaredField".equals(name)) {
						// [..., Class, String]
						super.visitInsn(Opcodes.SWAP);
						// [..., String, Class]
						super.visitInsn(Opcodes.DUP_X1);
						// [..., Class, String, Class]
						super.visitMethodInsn(
							Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixDeclaredFieldName",
							"(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/String;", false
						);
						// [..., Class, String]
						stackIncrease = Math.max(stackIncrease, 1);
					} else if ("getField".equals(name)) {
						// [..., Class, String]
						super.visitInsn(Opcodes.SWAP);
						// [..., String, Class]
						super.visitInsn(Opcodes.DUP_X1);
						// [..., Class, String, Class]
						super.visitMethodInsn(
							Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixFieldName",
							"(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/String;", false
						);
						// [..., Class, String]
						stackIncrease = Math.max(stackIncrease, 1);
					} else if ("getDeclaredMethod".equals(name)) {
						// ... , Class, String, Class[]
						super.visitMethodInsn(
							Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixDeclaredMethodName",
							"(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)L"
							+ OWNER_REFLECTIVE_MTH
							+ ";", false
						);
						// ... , TMP
						super.visitInsn(Opcodes.DUP);
						// ... , TMP, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getOwner", "()Ljava/lang/Class;", false
						);
						// ... , TMP, Class
						super.visitInsn(Opcodes.SWAP);
						// ... , Class, TMP
						super.visitInsn(Opcodes.DUP);
						// ... , Class, TMP, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getMethodName", "()Ljava/lang/String;", false
						);
						// ... , Class, TMP, String
						super.visitInsn(Opcodes.SWAP);
						// ... , Class, String, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getArgs", "()[Ljava/lang/Class;", false
						);
						// ... , Class, String, Class[]
					} else if ("getMethod".equals(name)) {
						// ... , Class, String, Class[]
						super.visitMethodInsn(
							Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixMethodName",
							"(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)L"
							+ OWNER_REFLECTIVE_MTH
							+ ";", false
						);
						// ... , TMP
						super.visitInsn(Opcodes.DUP);
						// ... , TMP, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getOwner", "()Ljava/lang/Class;", false
						);
						// ... , TMP, Class
						super.visitInsn(Opcodes.SWAP);
						// ... , Class, TMP
						super.visitInsn(Opcodes.DUP);
						// ... , Class, TMP, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getMethodName", "()Ljava/lang/String;", false
						);
						// ... , Class, TMP, String
						super.visitInsn(Opcodes.SWAP);
						// ... , Class, String, TMP
						super.visitMethodInsn(
							Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_MTH, "getArgs", "()[Ljava/lang/Class;", false
						);
						// ... , Class, String, Class[]
					}
				} else if (OWNER_LOOKUP.equals(owner)) {

					switch (name) {
						case "findClass": {
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixClassName",
								"(Ljava/lang/String;)Ljava/lang/String;", false
							);
							// In theory we could check the lookup to make sure we have permission
							// But that just seems unnecessary
							break;
						}
						case "findGetter":
						case "findSetter":
						case "findVarHandle":
						case "findStaticGetter":
						case "findStaticSetter":
						case "findStaticVarHandle": {
							// All of these methods have the same args
							// [..., Lookup, Class(owner), String(name), Class(type) ]
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixLookupFindField",
								"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)L" + OWNER_REFLECTIVE_TMP + ";",
								false
							);
							// [..., TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false
							);
							// [..., TMP, Lookup]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getOwner", "()Ljava/lang/Class;", false
							);
							// [..., Lookup, TMP, Class(owner)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, Class(owner), TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getName", "()Ljava/lang/String;", false
							);
							// [..., Lookup, Class(owner), TMP, String(name)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), String(name), TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getFieldType", "()Ljava/lang/Class;",
								false
							);
							// [..., Lookup, Class(owner), String(name), Class(type)]
							break;
						}
						case "findStatic":
						case "findVirtual": {
							// [..., Lookup, Class(owner), String(name), MethodType]
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixLookupFindMethod",
								"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)L"
									+ OWNER_REFLECTIVE_TMP + ";", false
							);
							// [..., TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false
							);
							// [..., TMP, Lookup]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getOwner", "()Ljava/lang/Class;", false
							);
							// [..., Lookup, TMP, Class(owner)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, Class(owner), TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getName", "()Ljava/lang/String;", false
							);
							// [..., Lookup, Class(owner), TMP, String(name)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), String(name), TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getMethodType",
								"()Ljava/lang/invoke/MethodType;", false
							);
							// [..., Lookup, Class(owner), String(name), MethodType]
							break;
						}
						case "findSpecial": {
							// [..., Lookup, Class(owner), String(name), MethodType, Class(special)]
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, OWNER_REFLECTIVE_UTIL, "fixLookupFindMethodSpecial",
								"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)L"
									+ OWNER_REFLECTIVE_TMP + ";", false
							);
							// [..., TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false
							);
							// [..., TMP, Lookup]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getOwner", "()Ljava/lang/Class;", false
							);
							// [..., Lookup, TMP, Class(owner)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, Class(owner), TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getName", "()Ljava/lang/String;", false
							);
							// [..., Lookup, Class(owner), TMP, String(name)]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), String(name), TMP]
							super.visitInsn(Opcodes.DUP);
							// [..., Lookup, Class(owner), String(name), TMP, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getMethodType",
								"()Ljava/lang/invoke/MethodType;", false
							);
							// [..., Lookup, Class(owner), String(name), TMP, MethodType]
							super.visitInsn(Opcodes.SWAP);
							// [..., Lookup, Class(owner), String(name), MethodType, TMP]
							super.visitMethodInsn(
								Opcodes.INVOKEVIRTUAL, OWNER_REFLECTIVE_TMP, "getSpecialCaller",
								"()Ljava/lang/Class;", false
							);
							// [..., Lookup, Class(owner), String(name), MethodType, Class(special)]
							break;
						}
						default: {
							break;
						}
					}
				}

				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			}

			@Override
			public void visitMaxs(int maxStack, int maxLocals) {
				// Ideally we'd track the actual stack value to figure out if we truly need to increase it
				// But this is *much* easier
				super.visitMaxs(maxStack + stackIncrease, maxLocals);
			}
		};
	}
}
