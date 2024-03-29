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
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.Requires;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class PackageStrippingData extends ClassVisitor {

	private static final String CLIENT_ONLY_DESCRIPTOR = Type.getDescriptor(ClientOnly.class);
	private static final String SERVER_ONLY_DESCRIPTOR = Type.getDescriptor(DedicatedServerOnly.class);
	private static final String REQUIRES_DESCRIPTOR = Type.getDescriptor(Requires.class);

	private final EnvType envType;
	private final List<String> mods;

	private boolean stripEntirePackage = false;

	public PackageStrippingData(int api, EnvType envType, Map<String, String> modCodeSourceMap) {
		super(api);
		this.envType = envType;
		this.mods = new ArrayList<>(modCodeSourceMap.keySet());
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (CLIENT_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.SERVER) {
				stripEntirePackage = true;
			}
		} else if (SERVER_ONLY_DESCRIPTOR.equals(descriptor)) {
			if (envType == EnvType.CLIENT) {
				stripEntirePackage = true;
			}
		} else if (REQUIRES_DESCRIPTOR.equals(descriptor)) {
			return new AnnotationVisitor(api) {
				@Override
				public AnnotationVisitor visitArray(String name) {
					if ("value".equals(name)) {
						return new AnnotationVisitor(api) {
							@Override
							public void visit(String name, Object value) {
								if (!mods.contains(String.valueOf(value))) {
									stripEntirePackage = true;
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
		return null;
	}

	public boolean stripEntirePackage() {
		return this.stripEntirePackage;
	}
}
