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

import java.lang.invoke.LambdaMetafactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class LambdaStripCalculator extends ClassVisitor {

	private static final String LAMBDA_CLASS_NAME = Type.getInternalName(LambdaMetafactory.class);
	private static final String LAMBDA_METHOD_DESCRIPTOR
		= "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

	private final Collection<String> methodsToStripFrom;
	private final Set<String> methodsToStrip = new HashSet<>();
	private final Set<String> methodsToNotStrip = new HashSet<>();

	String className = "";

	public LambdaStripCalculator(int api, Collection<String> methodsToStripFrom) {
		super(api);
		this.methodsToStripFrom = methodsToStripFrom;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.className = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
		String[] exceptions) {

		// Since it's possible for multiple methods to use the same bootstrap
		// we check every method.
		if (methodsToStripFrom.contains(name + descriptor)) {
			return new LambdaMethodVisitor(api, methodsToStrip);
		} else {
			return new LambdaMethodVisitor(api, methodsToNotStrip);
		}
	}

	public Collection<String> computeAdditionalMethodsToStrip() {
		Set<String> toStrip = new HashSet<>();
		toStrip.addAll(methodsToStrip);
		toStrip.removeAll(methodsToNotStrip);
		return toStrip;
	}

	private class LambdaMethodVisitor extends MethodVisitor {

		final Set<String> methods;

		public LambdaMethodVisitor(int api, Set<String> methods) {
			super(api);
			this.methods = methods;
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
			Object... bootstrapMethodArguments) {

			if (Opcodes.H_INVOKESTATIC != bootstrapMethodHandle.getTag()) {
				return;
			}

			if (!"metafactory".equals(bootstrapMethodHandle.getName())) {
				return;
			}

			if (!LAMBDA_CLASS_NAME.equals(bootstrapMethodHandle.getOwner())) {
				return;
			}

			if (!LAMBDA_METHOD_DESCRIPTOR.equals(bootstrapMethodHandle.getDesc())) {
				return;
			}

			if (bootstrapMethodArguments.length == 3) {
				// We expect the last 3 arguments of the "metafactory" method to be passed here
				// The second one here will be the method handle of the lambda itself
				if (bootstrapMethodArguments[1] instanceof Handle) {
					Handle lambdaTarget = (Handle) bootstrapMethodArguments[1];
					if (lambdaTarget.getOwner().equals(className)) {
						methods.add(lambdaTarget.getName() + lambdaTarget.getDesc());
					}
				}
			}
		}

	}
}
