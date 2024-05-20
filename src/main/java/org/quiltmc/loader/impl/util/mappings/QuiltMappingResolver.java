/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.impl.util.mappings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.fabricmc.mappingio.tree.MappingTreeView;

import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltMappingResolver implements MappingResolver {
	private final MappingTreeView tree;
	private final String targetNamespace;
	private final int targetNamespaceId;
	private final List<String> namespaces;

	public QuiltMappingResolver(MappingTreeView tree, String targetNamespace) {
		this.tree = tree;
		this.targetNamespace = targetNamespace;
		this.targetNamespaceId = tree.getNamespaceId(targetNamespace);
		List<String> namespaces = new ArrayList<>();
		namespaces.add(tree.getSrcNamespace());
		namespaces.addAll(tree.getDstNamespaces());
		this.namespaces = Collections.unmodifiableList(namespaces);
	}

	private static String slashToDot(String cname) {
		return cname.replace('/', '.');
	}

	private static String dotToSlash(String cname) {
		return cname.replace('.', '/');
	}

	// mapping-io appears to not throw an exception if it doesn't know the namespace
	private int safeGetId(String namespace) {
		int ret = tree.getNamespaceId(namespace);
		if (ret == -2) {
			throw new IllegalArgumentException("Unknown namespace " + namespace);
		}
		return ret;
	}

	@Override
	public Collection<String> getNamespaces() {
		return namespaces;
	}

	@Override
	public String getCurrentRuntimeNamespace() {
		return targetNamespace;
	}

	@Override
	public String mapClassName(String fromNamespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return slashToDot(tree.mapClassName(dotToSlash(className), safeGetId(fromNamespace), targetNamespaceId));
	}

	@Override
	public String unmapClassName(String namespace, String className) {
		if (className.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
		}

		return slashToDot(tree.mapClassName(dotToSlash(className), targetNamespaceId, tree.getNamespaceId(namespace)));
	}

	@Override
	public String mapFieldName(String fromNamespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}
		MappingTreeView.FieldMappingView field = tree.getField(dotToSlash(owner), name, descriptor, safeGetId(fromNamespace));
		return field == null ? name : field.getName(targetNamespace);
	}

	@Override
	public String mapMethodName(String fromNamespace, String owner, String name, String descriptor) {
		if (owner.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
		}

		MappingTreeView.MethodMappingView method = tree.getMethod(dotToSlash(owner), name, descriptor, safeGetId(fromNamespace));
		return method == null ? name : method.getName(targetNamespace);
	}
}
