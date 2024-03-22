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

import java.util.HashMap;

import org.objectweb.asm.AnnotationVisitor;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;

/** Deprecated. All stuff were moved to {@link PackageStrippingData}. */
@Deprecated
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class PackageEnvironmentStrippingData extends PackageStrippingData {

	private static final String CLIENT_ONLY_DESCRIPTOR = PackageStrippingData.CLIENT_ONLY_DESCRIPTOR;
	private static final String SERVER_ONLY_DESCRIPTOR = PackageStrippingData.SERVER_ONLY_DESCRIPTOR;

	public PackageEnvironmentStrippingData(int api, EnvType envType) {
		super(api, envType, new HashMap<>());
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public boolean stripEntirePackage() {
		return super.stripEntirePackage();
	}
}
