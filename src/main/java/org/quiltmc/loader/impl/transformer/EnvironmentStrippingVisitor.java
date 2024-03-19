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
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.EnvironmentInterfaces;

import org.quiltmc.loader.impl.util.StrippingDataContainer;

/** Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class EnvironmentStrippingVisitor extends ClassVisitor {
	// Fabric annotations
	private static final String ENVIRONMENT_DESCRIPTOR = Type.getDescriptor(Environment.class);
	private static final String ENVIRONMENT_INTERFACE_DESCRIPTOR = Type.getDescriptor(EnvironmentInterface.class);
	private static final String ENVIRONMENT_INTERFACES_DESCRIPTOR = Type.getDescriptor(EnvironmentInterfaces.class);

	// Quilt annotations
	private static final String CLIENT_ONLY_DESCRIPTOR = Type.getDescriptor(ClientOnly.class);
	private static final String SERVER_ONLY_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnly.class);

	private final EnvType envType;
	private final String envTypeString;

	private String[] interfaces;

	private final StrippingDataContainer data;

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
				data.getStripInterfaces().add(itf.getInternalName());
			}
		}
	}

	private AnnotationVisitor visitMemberAnnotation(String descriptor, boolean visible, Runnable onEnvMismatch,
		Runnable onEnvMismatchLambdas) {
		if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentAnnotationVisitor(api, onEnvMismatch);
		}

		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor) && envType == EnvType.SERVER) {
			return new QuiltEnvironmentAnnotationVisitor(api, onEnvMismatch, onEnvMismatchLambdas);
		}

		if (SERVER_ONLY_DESCRIPTOR.equals(descriptor) && envType == EnvType.CLIENT) {
			return new QuiltEnvironmentAnnotationVisitor(api, onEnvMismatch, onEnvMismatchLambdas);
		}

		return null;
	}

	public EnvironmentStrippingVisitor(int api, StrippingDataContainer data, EnvType envType) {
		super(api);
		this.data = data;
		this.envType = envType;
		this.envTypeString = envType.name();
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.interfaces = interfaces;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.SERVER) {
				data.enableStripEntireClass();
			}
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.CLIENT) {
				data.enableStripEntireClass();
			}
		} else if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentAnnotationVisitor(api, data::enableStripEntireClass);
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

		final EnvType annotationEnv;

		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			annotationEnv = EnvType.CLIENT;
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			annotationEnv = EnvType.SERVER;
		} else {
			return null;
		}

		if (annotationEnv != envType) {
			data.getStripInterfaces().add(interfaces[interfaceIdx]);
		}

		return null;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return new FieldVisitor(api) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return visitMemberAnnotation(descriptor, visible, () -> data.getStripFields().add(name + descriptor), null);
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
					descriptor, visible, () -> data.getStripMethods().add(methodId), () -> data.getStripMethodLambdas().add(methodId)
				);
			}
		};
	}
}
