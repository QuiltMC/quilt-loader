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

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.quiltmc.loader.api.minecraft.Requires;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.StrippingDataContainer;

/** Scans a class for Environment and EnvironmentInterface annotations to figure out what needs to be stripped. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class RequiresStrippingVisitor extends ClassVisitor {

	private static final String REQUIRES_DESCRIPTOR = Type.getDescriptor(Requires.class);

	private final List<String> modList;

	private String[] interfaces;

	private final StrippingDataContainer data;

	private class QuiltRequiresAnnotationVisitor extends AnnotationVisitor {
		private final Runnable onModsMismatch;
		private final Runnable onModsMismatchLambdas;

		private boolean stripLambdas = true;

		private QuiltRequiresAnnotationVisitor(int api, Runnable onModsMismatch, Runnable onModsMismatchLambdas) {
			super(api);
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
				return new AnnotationVisitor(api) {

					@Override
					public void visit(String name, Object value) {
						if (!isModLoaded(String.valueOf(value), modList)) {
							onModsMismatch.run();

							if (stripLambdas && onModsMismatchLambdas != null) {
								onModsMismatchLambdas.run();
							}
						}
					}
				};
			}
			else {
				return null;
			}
		}
	}

	private AnnotationVisitor visitMemberAnnotation(String descriptor, boolean visible, Runnable onModsMismatch,
		Runnable onModsMismatchLambdas) {

		if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new QuiltRequiresAnnotationVisitor(api, onModsMismatch, onModsMismatchLambdas);
		}

		return null;
	}

	public RequiresStrippingVisitor(int api, StrippingDataContainer data, List<ModLoadOption> modList) {
		super(api);
		this.data = data;
		this.modList = new ArrayList<>();
		modList.stream().map(ModLoadOption::id).forEach(this.modList::add);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.interfaces = interfaces;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new QuiltRequiresAnnotationVisitor(api, data::enableStripEntireClass, null);
		}
		else {
			return null;
		}
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
			return new QuiltRequiresAnnotationVisitor(api, () -> data.getStripInterfaces().add(interfaces[interfaceIdx]), null);
		}
		else {
			return null;
		}
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

	private boolean isModLoaded(String requirement, List<String> loadedMods) {
		return loadedMods.contains(requirement);
	}
}
