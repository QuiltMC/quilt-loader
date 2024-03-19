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
import java.util.List;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.api.EnvType;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import net.fabricmc.accesswidener.AccessWidenerClassVisitor;

import org.quiltmc.loader.impl.util.StrippingDataContainer;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
final class QuiltTransformer {
	public static byte @Nullable [] transform(boolean isDevelopment, EnvType envType, TransformCache cache, AccessWidener accessWidener, String name, ModLoadOption mod, List<ModLoadOption> modList, byte[] bytes) {
		boolean isMinecraftClass = mod.id().equals("minecraft");
		boolean transformAccess = isMinecraftClass && QuiltLauncherBase.getLauncher().getMappingConfiguration().requiresPackageAccessHack();
		boolean strip = !isMinecraftClass || isDevelopment;
		boolean applyAccessWidener = isMinecraftClass && accessWidener.getTargets().contains(name);

		if (!transformAccess && !strip && !applyAccessWidener) {
			return bytes;
		}

		ClassReader classReader = new ClassReader(bytes);
		ClassWriter classWriter = null;
		ClassVisitor visitor = null;
		int visitorCount = 0;

		if (strip) {
			StrippingDataContainer stripData = new StrippingDataContainer();

			EnvironmentStrippingVisitor envStrip = new EnvironmentStrippingVisitor(QuiltLoaderImpl.ASM_VERSION, stripData, envType);
			classReader.accept(envStrip, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			RequiresStrippingVisitor requiresStrip = new RequiresStrippingVisitor(QuiltLoaderImpl.ASM_VERSION, stripData, modList);
			classReader.accept(requiresStrip, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

			if (stripData.stripEntireClass()) {
				cache.hideClass(name);
				return null;
			}

			Collection<String> stripMethods = stripData.getStripMethods();

			boolean stripAnyLambdas = false;

			if (!stripData.getStripMethodLambdas().isEmpty()) {
				LambdaStripCalculator calc = new LambdaStripCalculator(QuiltLoaderImpl.ASM_VERSION, stripData.getStripMethodLambdas());
				classReader.accept(calc, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				Collection<String> additionalStripMethods = calc.computeAdditionalMethodsToStrip();

				if (!additionalStripMethods.isEmpty()) {
					stripMethods = new HashSet<>(stripMethods);
					stripMethods.addAll(additionalStripMethods);

					stripAnyLambdas = true;
				}
			}

			if (!stripData.isEmpty()) {

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

				visitor = new ClassStripper(QuiltLoaderImpl.ASM_VERSION, classWriter, stripData.getStripInterfaces(), stripData.getStripFields(), stripMethods);
				visitorCount++;
			}
		}

		if (classWriter == null) {
			classWriter = new ClassWriter(classReader, 0);
			visitor = classWriter;
		}

		if (applyAccessWidener) {
			visitor = AccessWidenerClassVisitor.createClassVisitor(QuiltLoaderImpl.ASM_VERSION, visitor, accessWidener);
			visitorCount++;
		}

		if (transformAccess) {
			visitor = new PackageAccessFixer(QuiltLoaderImpl.ASM_VERSION, visitor);
			visitorCount++;
		}

		if (visitorCount <= 0) {
			return null;
		}

		classReader.accept(visitor, 0);
		return classWriter.toByteArray();
	}
}
