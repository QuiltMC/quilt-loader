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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.ModInternal;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.knot.IllegalQuiltInternalAccessError;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

class InternalsHiderTransform {

	private static final String MOD_INTERNAL_DESCRIPTOR = Type.getDescriptor(ModInternal.class);

	private static final String METHOD_OWNER = Type.getInternalName(QuiltInternalExceptionUtil.class);

	final Map<String, InternalValue> internalPackages = new HashMap<>();
	final Map<String, InternalValue> internalClasses = new HashMap<>();
	final Map<MethodKey, InternalValue> internalMethods = new HashMap<>();
	final Map<FieldKey, InternalValue> internalFields = new HashMap<>();

	InternalsHiderTransform() {}

	void scanClass(ModLoadOption mod, Path file, byte[] classBytes) {
		// TODO: Replace this with full-reflect lookup!
		ClassReader reader;
		try {
			reader = new ClassReader(classBytes);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed to read the class " + file + " from mod " + mod.id(), e);
		}
		String className = reader.getClassName();
		boolean isPackageInfo = className.endsWith("/package-info");
		ClassVisitor visitor = new ClassVisitor(QuiltLoaderImpl.ASM_VERSION) {

			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				if (!MOD_INTERNAL_DESCRIPTOR.equals(descriptor)) {
					return null;
				}
				return new ScanningAnnotationVisitor() {
					@Override
					public void visitEnd() {
						String key = className;
						if (isPackageInfo) {
							key = key.substring(0, className.length() - "/package-info".length());
						}
						put(mod, (isPackageInfo ? internalPackages : internalClasses), key);
					}
				};
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
				String[] exceptions) {

				return new MethodVisitor(api) {
					@Override
					public AnnotationVisitor visitAnnotation(String aDesc, boolean visible) {
						if (!MOD_INTERNAL_DESCRIPTOR.equals(aDesc)) {
							return null;
						}
						return new ScanningAnnotationVisitor() {
							@Override
							public void visitEnd() {
								put(mod, internalMethods, new MethodKey(className, name, descriptor));
							}
						};
					}
				};
			}

			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				return new FieldVisitor(api) {
					@Override
					public AnnotationVisitor visitAnnotation(String aDesc, boolean visible) {
						if (!MOD_INTERNAL_DESCRIPTOR.equals(aDesc)) {
							return null;
						}
						return new ScanningAnnotationVisitor() {
							@Override
							public void visitEnd() {
								put(mod, internalFields, new FieldKey(className, name, descriptor));
							}
						};
					}
				};
			}
		};
		reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	byte[] run(ModLoadOption mod, byte[] classBytes) {
		ClassReader reader = new ClassReader(classBytes);
		String className = reader.getClassName();
		ClassWriter writer = new ClassWriter(reader, 0) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				throw new Error("We shouldn't need to compute the superclass of " + type1 + ", " + type2);
			}
		};

		List<InternalSuper> illegalSupers = new ArrayList<>();
		checkSuper(mod, reader.getSuperName(), false, illegalSupers);
		for (String itf : reader.getInterfaces()) {
			checkSuper(mod, itf, true, illegalSupers);
		}

		boolean[] hasClassInit = { false };
		ClassVisitor visitor = new ClassVisitor(QuiltLoaderImpl.ASM_VERSION, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String mthName, String mthDescriptor, String signature,
				String[] exceptions) {

				MethodVisitor sup = super.visitMethod(access, mthName, mthDescriptor, signature, exceptions);

				boolean isClassInit = "<clinit>".equals(mthName) && "()V".equals(mthDescriptor);
				if (isClassInit) {
					hasClassInit[0] = true;
				}

				// SO
				// This is a two step process
				// we need to visit every method call, field read/write
				// and check that against the definitions, to see if the package/class/method/field is marked as
				// "@ModInternal"
				// (and check that against quilt loader itself)

				return new MethodVisitor(api, sup) {

					@Override
					public void visitCode() {
						super.visitCode();
						if (isClassInit && !illegalSupers.isEmpty()) {
							prefixClassInitErrors(sup);
						}
					}

					@Override
					public void visitMaxs(int maxStack, int maxLocals) {
						// Sadly nothing we can do about this, since errors might occur anywhere :/
						super.visitMaxs(maxStack + 1, maxLocals);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						if (owner.equals(className)) {
							// Always permitted
							super.visitFieldInsn(opcode, owner, name, descriptor);
							return;
						}

						InternalValue set = internalFields.get(new FieldKey(owner, name, descriptor));

						if (set == null) {
							set = getAnnotationSet(owner);
						}

						if (set != null && !set.isPermitted(mod)) {
							super.visitLdcInsn(set.generateError(mod, "the field " + owner + "." + name, className + "." + mthName + mthDescriptor));
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, METHOD_OWNER, set.getInvokeMethodName(), "(Ljava/lang/String;)V",
								false
							);
						}

						super.visitFieldInsn(opcode, owner, name, descriptor);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
						boolean isInterface) {

						if (owner.equals(className)) {
							// Always permitted
							super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
							return;
						}

						InternalValue set = internalMethods.get(new MethodKey(owner, name, descriptor));

						if (set == null) {
							set = getAnnotationSet(owner);
						}

						if (set != null && !set.isPermitted(mod)) {
							super.visitLdcInsn(
								set.generateError(mod, "the method " + owner + "." + name + descriptor, className + "." + mthName + mthDescriptor)
							);
							super.visitMethodInsn(
								Opcodes.INVOKESTATIC, METHOD_OWNER, set.getInvokeMethodName(),
								"(Ljava/lang/String;)V", false
							);
						}

						super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
					}
				};
			}

			@Override
			public void visitEnd() {
				if (!hasClassInit[0] && !illegalSupers.isEmpty()) {
					MethodVisitor classInit = super.visitMethod(
						Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "<clinit>", "()V", null, null
					);
					classInit.visitMaxs(1, 0);
					classInit.visitCode();
					prefixClassInitErrors(classInit);
					classInit.visitInsn(Opcodes.RETURN);
					classInit.visitEnd();
				}
				super.visitEnd();
			}

			private void prefixClassInitErrors(MethodVisitor to) {
				boolean onlyWarn = true;
				StringBuilder msg = new StringBuilder();
				for (InternalSuper value : illegalSupers) {
					if (!(value.value instanceof WarnLoaderInternalValue)) {
						onlyWarn = false;
					}
					msg.append(value.generateError(mod, className));
				}
				to.visitLdcInsn(msg.toString());
				to.visitMethodInsn(
					Opcodes.INVOKESTATIC, METHOD_OWNER, onlyWarn ? "warnInternalAccess" : "throwInternalAccess", "(Ljava/lang/String;)V", false
				);
			}
		};
		reader.accept(visitor, 0);
		return writer.toByteArray();
	}

	void finish() {

	}

	private InternalValue getAnnotationSet(String owner) {
		InternalValue value = internalClasses.get(owner);
		if (value != null) {
			return value;
		}

		if (owner.startsWith("org/quiltmc/loader/")) {
			try {
				String name = owner.replace('/', '.');
				Class<?> loaderClass = Class.forName(name);
				QuiltLoaderInternal internal = loaderClass.getAnnotation(QuiltLoaderInternal.class);
				QuiltLoaderInternalType type;
				Class<?>[] replacements = {};
				if (internal != null) {
					type = internal.value();
					replacements = internal.replacements();
				} else if (name.startsWith("org.quiltmc.loader.impl")) {
					type = QuiltLoaderInternalType.LEGACY_EXPOSED;
					Log.warn(LogCategory.GENERAL, loaderClass + " isn't annotated with @QuiltLoaderInternal!");
				} else if (name.startsWith("org.quiltmc.loader.api.plugin")) {
					type = QuiltLoaderInternalType.PLUGIN_API;
					Log.warn(LogCategory.GENERAL, loaderClass + " isn't annotated with @QuiltLoaderInternal!");
				} else {
					type = QuiltLoaderInternalType.LEGACY_NO_WARN;
				}

				if (type == QuiltLoaderInternalType.LEGACY_NO_WARN) {
					value = PermittedLoaderInternalValue.INSTANCE;
				} else {
					if (type == QuiltLoaderInternalType.LEGACY_EXPOSED) {
						value = new WarnLoaderInternalValue();
					} else {
						value = new LoaderInternalValue();
					}
					for (Class<?> cls : replacements) {
						value.replacements.add(cls.toString());
					}
				}
				internalClasses.put(owner, value);

				return value;

			} catch (ClassNotFoundException e) {
				// Not illegal
			}
		}

		int lastSlash = owner.lastIndexOf('/');
		if (lastSlash > 0) {
			value = internalPackages.get(owner.substring(0, lastSlash));
		}
		return value;
	}

	private void checkSuper(ModLoadOption mod, String superName, boolean isInterface, List<
		InternalSuper> illegalSupers) {
		if (superName == null) {
			return;
		}
		InternalValue annotationSet = getAnnotationSet(superName);
		if (annotationSet == null || annotationSet.isPermitted(mod)) {
			return;
		}
		illegalSupers.add(new InternalSuper(superName, isInterface, annotationSet));
	}

	static final class MethodKey {
		final String className;
		final String methodName;
		final String descriptor;

		public MethodKey(String className, String methodName, String descriptor) {
			this.className = className;
			this.methodName = methodName;
			this.descriptor = descriptor;
		}

		@Override
		public int hashCode() {
			return Objects.hash(className, descriptor, methodName);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			MethodKey other = (MethodKey) obj;
			return Objects.equals(className, other.className) && Objects.equals(descriptor, other.descriptor) && Objects
				.equals(methodName, other.methodName);
		}
	}

	static final class FieldKey {
		final String className;
		final String fieldName;
		final String type;

		public FieldKey(String className, String fieldName, String type) {
			this.className = className;
			this.fieldName = fieldName;
			this.type = type;
		}

		@Override
		public int hashCode() {
			return Objects.hash(className, fieldName, type);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			FieldKey other = (FieldKey) obj;
			return Objects.equals(className, other.className) && Objects.equals(fieldName, other.fieldName) && Objects
				.equals(type, other.type);
		}
	}

	static abstract class InternalValue {

		final List<String> replacements = new ArrayList<>();

		abstract boolean isPermitted(ModLoadOption mod);

		abstract String modFrom();

		String getInvokeMethodName() {
			return "throwInternalAccess";
		}

		String generateError(ModLoadOption from, String target, String site) {
			StringBuilder sb = new StringBuilder();
			sb.append("Found illegal access from ");
			sb.append(from != null ? from.metadata().name() : "Unknown Mod");
			sb.append(" to ");
			sb.append(modFrom());
			sb.append("\n class ");
			sb.append(site);
			sb.append("\n accessed ");
			sb.append(target);
			sb.append("\n");

			if (replacements.isEmpty()) {
				sb.append("Please don't use this, instead ask ");
				sb.append(modFrom());
				sb.append(" to declare a new public API that can have guarenteed backwards compatibility!");
			} else if (replacements.size() == 1) {
				sb.append("Please don't use this, instead try using the public api ");
				sb.append(replacements.get(0));
				sb.append(" - that way you can have guarenteed backwards compatibility!");
			} else {
				sb.append("Please don't use this, instead try using one of the following public API classes:");
				for (String str : replacements) {
					sb.append("\n - ");
					sb.append(str);
				}
			}

			return sb.toString();
		}
	}

	static class LoaderInternalValue extends InternalValue {
		@Override
		boolean isPermitted(ModLoadOption mod) {
			return false;
		}

		@Override
		String modFrom() {
			return "Quilt Loader";
		}
	}

	static final class PermittedLoaderInternalValue extends LoaderInternalValue {
		static final PermittedLoaderInternalValue INSTANCE = new PermittedLoaderInternalValue();

		@Override
		boolean isPermitted(ModLoadOption mod) {
			return true;
		}
	}

	static final class WarnLoaderInternalValue extends LoaderInternalValue {
		static final WarnLoaderInternalValue INSTANCE = new WarnLoaderInternalValue();

		@Override
		String getInvokeMethodName() {
			return "warnInternalAccess";
		}
	}

	static final class ModInternalValue extends InternalValue {
		final ModLoadOption inMod;
		final Set<String> permitted;

		public ModInternalValue(ModLoadOption inMod, Set<String> permitted) {
			this.inMod = inMod;
			this.permitted = permitted;
		}

		@Override
		boolean isPermitted(ModLoadOption mod) {
			return mod != null && permitted.contains(mod.id());
		}

		@Override
		String modFrom() {
			return inMod == null ? "Unkown Mod" : inMod.metadata().name();
		}
	}

	static final class InternalSuper {
		final String superName;
		final boolean isInterface;
		final InternalValue value;

		public InternalSuper(String superName, boolean isInterface, InternalValue value) {
			this.superName = superName;
			this.isInterface = isInterface;
			this.value = value;
		}

		String generateError(ModLoadOption from, String site) {
			return value.generateError(from, (isInterface ? "the interface " : "the class ") + superName, site);
		}
	}

	static abstract class ScanningAnnotationVisitor extends AnnotationVisitor {
		final List<String> exceptions = new ArrayList<>();
		final List<String> replacements = new ArrayList<>();
		final List<String> classReplacements = new ArrayList<>();

		protected ScanningAnnotationVisitor() {
			super(QuiltLoaderImpl.ASM_VERSION);
		}

		@Override
		public void visit(String name, Object value) {

		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			return new AnnotationVisitor(api) {
				@Override
				public void visit(String ignored, Object value) {
					if ("exceptions".equals(name) && value instanceof String) {
						exceptions.add((String) value);
					}

					if ("replacements".equals(name) && value instanceof String) {
						replacements.add((String) value);
					}

					if ("classReplacements".equals(name) && value instanceof Type) {
						Type type = (Type) value;
						if (type.getSort() == Type.OBJECT) {
							classReplacements.add(type.getClassName());
						}
					}
				}
			};
		}

		@Override
		public abstract void visitEnd();

		protected final <K> void put(ModLoadOption mod, Map<K, InternalValue> map, K key) {
			Set<String> set = new HashSet<>();
			if (mod != null) {
				set.add(mod.id());
			}
			set.addAll(exceptions);
			ModInternalValue value = new ModInternalValue(mod, set);
			value.replacements.addAll(classReplacements);
			value.replacements.addAll(replacements);
			map.put(key, value);
		}
	}
}
