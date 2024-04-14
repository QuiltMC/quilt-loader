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
import java.util.Collection;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;

/** Deprecated. All stuff were moved to {@link ClassStrippingData}. */
@Deprecated
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class EnvironmentStrippingData extends ClassStrippingData {

	public EnvironmentStrippingData(int api, EnvType envType) {
		super(api, envType, new ArrayList<>());
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(access, name, descriptor, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	public boolean stripEntireClass() {
		return super.stripEntireClass();
	}

	@Override
	public Collection<String> getStripInterfaces() {
		return super.getStripInterfaces();
	}

	@Override
	public Collection<String> getStripFields() {
		return super.getStripFields();
	}

	@Override
	public Collection<String> getStripMethods() {
		return super.getStripMethods();
	}

	@Override
	public Collection<String> getStripMethodLambdas() {
		return super.getStripMethodLambdas();
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty();
	}
}
