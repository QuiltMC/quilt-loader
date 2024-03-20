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
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.minecraft.Requires;
import org.quiltmc.loader.impl.util.PackageStrippingDataContainer;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class PackageRequiresStrippingVisitor extends ClassVisitor {

	private static final String REQUIRES_DESCRIPTOR = Type.getDescriptor(Requires.class);

	private final List<String> modList;

	private final PackageStrippingDataContainer data;

	public PackageRequiresStrippingVisitor(int api, PackageStrippingDataContainer data, Map<String, String> modCodeSourceMap) {
		super(api);
		this.data = data;
		this.modList = new ArrayList<>();
		modCodeSourceMap.forEach((k, v) -> this.modList.add(k));
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new AnnotationVisitor(api) {
				@Override
				public AnnotationVisitor visitArray(String name) {
					if ("mods".equals(name)) {
						return new AnnotationVisitor(api) {
							@Override
							public void visit(String name, Object value) {
								if (!modList.contains(String.valueOf(value))) {
									data.enableStripEntirePackage();
								}
							}
						};
					}
					else {
						return null;
					}
				}
			};
		}
		else {
			return null;
		}
	}
}
