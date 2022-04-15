/*
 * Copyright 2016 FabricMC
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

import java.util.Collection;
import java.util.HashSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.ClientOnlyInterface;
import org.quiltmc.loader.api.minecraft.ClientOnlyInterfaces;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnlyInterface;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnlyInterfaces;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.EnvironmentInterfaces;

/** Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped. */
public class EnvironmentStrippingData extends ClassVisitor {
	// Fabric annotations
	private static final String ENVIRONMENT_DESCRIPTOR = Type.getDescriptor(Environment.class);
	private static final String ENVIRONMENT_INTERFACE_DESCRIPTOR = Type.getDescriptor(EnvironmentInterface.class);
	private static final String ENVIRONMENT_INTERFACES_DESCRIPTOR = Type.getDescriptor(EnvironmentInterfaces.class);

	// Quilt annotations
	private static final String CLIENT_ONLY_DESCRIPTOR = Type.getDescriptor(ClientOnly.class);
	private static final String SERVER_ONLY_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnly.class);
	private static final String CLIENT_ONLY_INTERFACE_DESCRIPTOR = Type.getDescriptor(ClientOnlyInterface.class);
	private static final String CLIENT_ONLY_INTERFACES_DESCRIPTOR = Type.getDescriptor(ClientOnlyInterfaces.class);
	private static final String SERVER_ONLY_INTERFACE_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnlyInterface.class);
	private static final String SERVER_ONLY_INTERFACES_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnlyInterfaces.class);

	private final EnvType envType;
	private final String envTypeString;

	private boolean stripEntireClass = false;
	private final Collection<String> stripInterfaces = new HashSet<>();
	private final Collection<String> stripFields = new HashSet<>();
	private final Collection<String> stripMethods = new HashSet<>();

	/** Every method contained in this will also be contained in {@link #stripMethods}. */
	private final Collection<String> stripMethodLambdas = new HashSet<>();

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

		private QuiltEnvironmentAnnotationVisitor(int api, Runnable onEnvMismatch, Runnable onEnvMismatchLambda) {
			super(api);
			this.onEnvMismatch = onEnvMismatch;
			this.onEnvMismatchLambdas = onEnvMismatchLambda;
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
				stripInterfaces.add(itf.getInternalName());
			}
		}
	}

	private class QuiltInterfaceAnnotationVisitor extends AnnotationVisitor {
		private Type itf;

		private QuiltInterfaceAnnotationVisitor(int api) {
			super(api);
		}

		@Override
		public void visit(String name, Object value) {
			if ("value".equals(name)) {
				itf = (Type) value;
			}
		}

		@Override
		public void visitEnd() {
			stripInterfaces.add(itf.getInternalName());
		}
	}

	private class QuiltMultiInterfaceAnnotationVisitor extends AnnotationVisitor {
		private QuiltMultiInterfaceAnnotationVisitor(int api) {
			super(api);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if ("value".equals(name)) {
				return new AnnotationVisitor(api) {
					@Override
					public AnnotationVisitor visitAnnotation(String name, String descriptor) {
						return new QuiltInterfaceAnnotationVisitor(api);
					}
				};
			}

			return null;
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

	public EnvironmentStrippingData(int api, EnvType envType) {
		super(api);
		this.envType = envType;
		this.envTypeString = envType.name();
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.SERVER) {
				stripEntireClass = true;
			}
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.CLIENT) {
				stripEntireClass = true;
			}
		} else if (ENVIRONMENT_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentAnnotationVisitor(api, () -> stripEntireClass = true);
		} else if (ENVIRONMENT_INTERFACE_DESCRIPTOR.equals(descriptor)) {
			return new FabricEnvironmentInterfaceAnnotationVisitor(api);
		} else if (CLIENT_ONLY_INTERFACE_DESCRIPTOR.equals(descriptor) && envType == EnvType.SERVER) {
			return new QuiltInterfaceAnnotationVisitor(api);
		} else if (SERVER_ONLY_INTERFACE_DESCRIPTOR.equals(descriptor) && envType == EnvType.CLIENT) {
			return new QuiltInterfaceAnnotationVisitor(api);
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

		if (CLIENT_ONLY_INTERFACES_DESCRIPTOR.equals(descriptor) && envType == EnvType.SERVER) {
			return new QuiltMultiInterfaceAnnotationVisitor(api);
		} else if (SERVER_ONLY_INTERFACES_DESCRIPTOR.equals(descriptor) && envType == EnvType.CLIENT) {
			return new QuiltMultiInterfaceAnnotationVisitor(api);
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
		return stripEntireClass;
	}

	public Collection<String> getStripInterfaces() {
		return stripInterfaces;
	}

	public Collection<String> getStripFields() {
		return stripFields;
	}

	public Collection<String> getStripMethods() {
		return stripMethods;
	}

	public Collection<String> getStripMethodLambdas() {
		return stripMethodLambdas;
	}

	public boolean isEmpty() {
		return stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty();
	}
}
