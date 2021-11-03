package net.fabricmc.loader.impl.quiltmc;

import java.util.Collection;

import org.quiltmc.loader.api.MappingResolver;

public class Quilt2FabricMappingResolver implements net.fabricmc.loader.api.MappingResolver {
	private final MappingResolver quilt;

	public Quilt2FabricMappingResolver(MappingResolver quilt) {
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
