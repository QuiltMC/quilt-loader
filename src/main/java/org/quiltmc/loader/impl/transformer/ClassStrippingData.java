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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.quiltmc.loader.api.Requires;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.EnvironmentInterfaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Scans a class for Environment, EnvironmentInterface and Requires annotations to figure out what needs to be stripped. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ClassStrippingData extends AbstractStripData {

	// Fabric annotations
	private static final String ENVIRONMENT_DESCRIPTOR = Type.getDescriptor(Environment.class);
	private static final String ENVIRONMENT_INTERFACE_DESCRIPTOR = Type.getDescriptor(EnvironmentInterface.class);
	private static final String ENVIRONMENT_INTERFACES_DESCRIPTOR = Type.getDescriptor(EnvironmentInterfaces.class);

	// Quilt annotations
	private static final String CLIENT_ONLY_DESCRIPTOR = Type.getDescriptor(ClientOnly.class);
	private static final String SERVER_ONLY_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnly.class);
	private static final String REQUIRES_DESCRIPTOR = Type.getDescriptor(Requires.class);

	private final String envTypeString;

	private final Collection<String> stripInterfaces = new HashSet<>();
	private final Collection<String> stripFields = new HashSet<>();
	private final Collection<String> stripMethods = new HashSet<>();

	/** Every method contained in this will also be contained in {@link #stripMethods}. */
	final Collection<String> stripMethodLambdas = new HashSet<>();

	private String type = "class";
	private String[] interfaces;

	private class FabricEnvironmentAnnotationVisitor extends AnnotationVisitor {
		private final Runnable onEnvMismatch;

		private FabricEnvironmentAnnotationVisitor(int api, Runnable onEnvMismatch) {
			super(api);
			this.onEnvMismatch = onEnvMismatch;
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			if ("value".equals(name) && !envTypeString.equals(value)) {
				onEnvMismatch.run();
			}
		}
	}

	private class FabricEnvironmentInterfaceAnnotationVisitor extends AnnotationVisitor {
		private boolean envMismatch;
		private Type itf;

		private FabricEnvironmentInterfaceAnnotationVisitor(int api) {
			super(api);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			if ("value".equals(name) && !envTypeString.equals(value)) {
				envMismatch = true;
			}
		}

		@Override
		public void visit(String name, Object value) {
			if ("itf".equals(name)) {
				itf = (Type) value;
			}
		}

		@Override
		public void visitEnd() {
			if (envMismatch) {
				stripInterfaces.add(itf.getInternalName());
			}
		}
	}

	private class QuiltEnvironmentAnnotationVisitor extends AnnotationVisitor {
		private final Runnable onEnvMismatch;
		private final Runnable onEnvMismatchLambdas;

		private boolean stripLambdas = true;

		private QuiltEnvironmentAnnotationVisitor(int api, Runnable onEnvMismatch, Runnable onEnvMismatchLambdas) {
			super(api);
			this.onEnvMismatch = onEnvMismatch;
			this.onEnvMismatchLambdas = onEnvMismatchLambdas;
		}

		@Override
		public void visit(String name, Object value) {
			if ("stripLambdas".equals(name) && Boolean.FALSE.equals(value)) {
				stripLambdas = false;
			}
		}

		@Override
		public void visitEnd() {
			onEnvMismatch.run();
			if (stripLambdas && onEnvMismatchLambdas != null) {
				onEnvMismatchLambdas.run();
			}
		}
	}

	@FunctionalInterface
	private interface OnModsMissing {
		void onModsMissing(List<String> required, List<String> missing);
	}

	private class QuiltRequiresAnnotationVisitor extends AnnotationVisitor {
		private final OnModsMissing onModsMissingDetailed;
		private final Runnable onModsMismatch;
		private final Runnable onModsMismatchLambdas;

		private boolean stripLambdas = true;

		private QuiltRequiresAnnotationVisitor(int api, OnModsMissing onModsMissing) {
			super(api);
			this.onModsMissingDetailed = onModsMissing;
			this.onModsMismatch = null;
			this.onModsMismatchLambdas = null;
		}

		private QuiltRequiresAnnotationVisitor(int api, Runnable onModsMismatch, Runnable onModsMismatchLambdas) {
			super(api);
			this.onModsMissingDetailed = null;
			this.onModsMismatch = onModsMismatch;
			this.onModsMismatchLambdas = onModsMismatchLambdas;
		}

		@Override
		public void visit(String name, Object value) {
			if ("stripLambdas".equals(name) && Boolean.FALSE.equals(value)) {
				stripLambdas = false;
			}
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if ("value".equals(name)) {
				if (onModsMissingDetailed == null) {
					return new AnnotationVisitor(api) {

						boolean anyMissing = false;

						@Override
						public void visit(String name, Object value) {
							if (!mods.contains(String.valueOf(value))) {
								anyMissing = true;
							}
						}

						@Override
						public void visitEnd() {
							if (anyMissing) {
								onModsMismatch.run();

								if (stripLambdas && onModsMismatchLambdas != null) {
									onModsMismatchLambdas.run();
								}
							}
						}
					};
				} else {
					return new AnnotationVisitor(api) {
						List<String> requiredMods = new ArrayList<>();
						List<String> missingMods = new ArrayList<>();

						@Override
						public void visit(String name, Object value) {
							String mod = String.valueOf(value);
							requiredMods.add(mod);
							if (!mods.contains(mod)) {
								missingMods.add(mod);
							}
						}

						@Override
						public void visitEnd() {
							if (!missingMods.isEmpty()) {
								onModsMissingDetailed.onModsMissing(requiredMods, missingMods);
							}
						}
					};
				}
			}
			else {
				return null;
			}
		}
	}

	private AnnotationVisitor visitMemberAnnotation(String descriptor, boolean visible, Runnable onMismatch, Runnable onMismatchLambdas) {

		if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentAnnotationVisitor(api, onMismatch);
		}

		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor) && envType == EnvType.SERVER) {
			return new QuiltEnvironmentAnnotationVisitor(api, onMismatch, onMismatchLambdas);
		}

		if (SERVER_ONLY_DESCRIPTOR.equals(descriptor) && envType == EnvType.CLIENT) {
			return new QuiltEnvironmentAnnotationVisitor(api, onMismatch, onMismatchLambdas);
		}

		if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new QuiltRequiresAnnotationVisitor(api, onMismatch, onMismatchLambdas);
		}

		return null;
	}

	public ClassStrippingData(int api, EnvType envType, List<ModLoadOption> mods) {
		super(api, envType, mods.stream().map(ModLoadOption::id).collect(Collectors.toSet()));
		this.envTypeString = envType.name();
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.interfaces = interfaces;

		if (name.endsWith("/package-info")) {
			type = "package";
		} else if ((access & Opcodes.ACC_ENUM) != 0) {
			type = "enum";
		} else if ((access & Opcodes.ACC_RECORD) != 0) {
			type = "record";
		} else if ((access & Opcodes.ACC_INTERFACE) != 0) {
			type = "interface";
		} else if ((access & Opcodes.ACC_ANNOTATION) != 0) {
			type = "annotation";
		} else {
			type = "class";
		}
	}

	@Override
	protected String type() {
		return type;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.SERVER) {
				denyClientOnlyLoad();
			}
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.CLIENT) {
				denyDediServerOnlyLoad();
			}
		} else if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new QuiltRequiresAnnotationVisitor(api, this::checkHasAllMods);
		} else if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentAnnotationVisitor(api, () -> denyLoadReasons.add("Mismatched @Envrionment"));
		} else if (ENVIRONMENT_INTERFACE_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentInterfaceAnnotationVisitor(api);
		} else if (ENVIRONMENT_INTERFACES_DESCRIPTOR.equals(descriptor)) {
			return new AnnotationVisitor(api) {
				@Override
				public AnnotationVisitor visitArray(String name) {
					if ("value".equals(name)) {
						return new AnnotationVisitor(api) {
							@Override
							public AnnotationVisitor visitAnnotation(String name, String descriptor) {
								return new FabricEnvironmentInterfaceAnnotationVisitor(api);
							}
						};
					}

					return null;
				}
			};
		}

		return null;
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {

		TypeReference ref = new TypeReference(typeRef);

		if (ref.getSort() != TypeReference.CLASS_EXTENDS) {
			return null;
		}

		int interfaceIdx = ref.getSuperTypeIndex();

		if (interfaceIdx < 0) {
			// Wrongly applied to the super class
			return null;
		}

		if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new QuiltRequiresAnnotationVisitor(api, () -> stripInterfaces.add(interfaces[interfaceIdx]), null);
		}

		final EnvType annotationEnv;

		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			annotationEnv = EnvType.CLIENT;
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			annotationEnv = EnvType.SERVER;
		} else {
			return null;
		}

		if (annotationEnv != envType) {
			stripInterfaces.add(interfaces[interfaceIdx]);
		}

		return null;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return new FieldVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return visitMemberAnnotation(descriptor, visible, () -> stripFields.add(name + descriptor), null);
			}
		};
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
		String[] exceptions) {
		String methodId = name + descriptor;
		return new MethodVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return visitMemberAnnotation(
					descriptor, visible, () -> stripMethods.add(methodId), () -> stripMethodLambdas.add(methodId)
				);
			}
		};
	}

	public boolean stripEntireClass() {
		return denyLoadReasons.size() > 0;
	}

	public Collection<String> getStripInterfaces() {
		return this.stripInterfaces;
	}

	public Collection<String> getStripFields() {
		return this.stripFields;
	}

	public Collection<String> getStripMethods() {
		return this.stripMethods;
	}

	public Collection<String> getStripMethodLambdas() {
		return this.stripMethodLambdas;
	}

	public boolean isEmpty() {
		return this.stripInterfaces.isEmpty() && this.stripFields.isEmpty() && this.stripMethods.isEmpty();
	}
}
