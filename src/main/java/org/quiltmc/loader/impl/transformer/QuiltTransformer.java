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

import java.util.Collection;
import java.util.HashSet;

import net.fabricmc.api.EnvType;

import net.fabricmc.classtweaker.api.ClassTweaker;

import net.fabricmc.classtweaker.classvisitor.AccessWidenerClassVisitor;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
final class QuiltTransformer {
	public static byte @Nullable [] transform(boolean isDevelopment, EnvType envType, TransformCache cache, ClassTweaker classTweaker, String name, ModLoadOption mod, byte[] bytes) {
		GameProvider gameProvider = QuiltLoaderImpl.INSTANCE.getGameProvider();
		boolean isGameClass = mod.id().equals(gameProvider.getGameId());
		boolean transformAccess = isGameClass && gameProvider.requiresPackageAccessFix();
		boolean strip = !isGameClass || isDevelopment;
		boolean applyClassTweaker = isGameClass && classTweaker.getTargets().contains(name.replace('.', '/'));
		boolean reflectiveFixes = !isGameClass && !Boolean.getBoolean(SystemProperties.DISABLE_REFLECTIVE_FIXES);

		if (!transformAccess && !strip && !applyClassTweaker && !reflectiveFixes) {
			return bytes;
		}

		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = null;
		ClassVisitor visitor = null;
		int visitorCount = 0;

		if (strip) {
			ClassStrippingData data = new ClassStrippingData(QuiltLoaderImpl.ASM_VERSION, envType, cache.getAllMods());
			classReader.accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			if (data.stripEntireClass()) {
				cache.hideClass(name, data.summarizeDenyLoadReasons());
				return null;
			}

			Collection<String> stripMethods = data.getStripMethods();

			boolean stripAnyLambdas = false;

			if (!data.getStripMethodLambdas().isEmpty()) {
				LambdaStripCalculator calc = new LambdaStripCalculator(QuiltLoaderImpl.ASM_VERSION, data.getStripMethodLambdas());
				classReader.accept(calc, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				Collection<String> additionalStripMethods = calc.computeAdditionalMethodsToStrip();

				if (!additionalStripMethods.isEmpty()) {
					stripMethods = new HashSet<>(stripMethods);
					stripMethods.addAll(additionalStripMethods);

					stripAnyLambdas = true;
				}
			}

			if (!data.isEmpty()) {

				if (stripAnyLambdas) {
					// ClassWriter has a (useful) optimisation that copies over the
					// entire constant pool and bootstrap methods from the original one,
					// as well as any untransformed methods.
					// However we can't use the second one, since we may need to remove bootstrap methods
					// that reference methods which are no longer present in the stripped version.
					classWriter = new ClassWriter(0);
				} else {
					classWriter = new ClassWriter(classReader, 0);
				}

				visitor = new ClassStripper(QuiltLoaderImpl.ASM_VERSION, classWriter, data.getStripInterfaces(), data.getStripFields(), stripMethods);
				visitorCount++;
			}
		}

		if (classWriter == null) {
			classWriter = new ClassWriter(classReader, 0);
			visitor = classWriter;
		}

		if (applyClassTweaker) {
			visitor = classTweaker.createClassVisitor(QuiltLoaderImpl.ASM_VERSION, visitor, null);
			visitorCount++;
		}

		if (transformAccess) {
			visitor = new PackageAccessFixer(QuiltLoaderImpl.ASM_VERSION, visitor);
			visitorCount++;
		}

		if (reflectiveFixes) {
			visitor = new ReflectiveFixer(QuiltLoaderImpl.ASM_VERSION, visitor);
			visitorCount++;
		}

		if (visitorCount <= 0) {
			return null;
		}

		classReader.accept(visitor, 0);
		return classWriter.toByteArray();
	}
}
