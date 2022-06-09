/*
 * Copyright 2016 FabricMC
 * Copyright 2022 QuiltMC
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

package net.fabricmc.loader.impl;

import java.util.Collection;

import org.quiltmc.loader.api.MappingResolver;

@Deprecated
public class MappingResolverImpl implements net.fabricmc.loader.api.MappingResolver {
	private final MappingResolver quilt;

	public MappingResolverImpl(MappingResolver quilt) {
		this.quilt = quilt;
	}

	@Override
	public Collection<String> getNamespaces() {
		return quilt.getNamespaces();
	}

	@Override
	public String getCurrentRuntimeNamespace() {
		return quilt.getCurrentRuntimeNamespace();
	}

	@Override
	public String mapClassName(String namespace, String className) {
		return quilt.mapClassName(namespace, className);
	}

	@Override
	public String unmapClassName(String targetNamespace, String className) {
		return quilt.unmapClassName(targetNamespace, className);
	}

	@Override
	public String mapFieldName(String namespace, String owner, String name, String descriptor) {
		return quilt.mapFieldName(namespace, owner, name, descriptor);
	}

	@Override
	public String mapMethodName(String namespace, String owner, String name, String descriptor) {
		return quilt.mapMethodName(namespace, owner, name, descriptor);
	}
}
