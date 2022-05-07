package org.quiltmc.loader.impl.config;

import org.quiltmc.loader.api.config.values.ComplexConfigValue;
import org.quiltmc.loader.api.config.values.ConfigSerializableObject;
import org.quiltmc.loader.api.config.values.ValueMap;

public class Vec3i implements ConfigSerializableObject<ValueMap<Integer>> {
	public final int x, y, z;

	public Vec3i(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public ComplexConfigValue copy() {
		return this;
	}

	@Override
	public Vec3i convertFrom(ValueMap<Integer> representation) {
		return new Vec3i(representation.get("x"), representation.get("y"), representation.get("z"));
	}

	@Override
	public ValueMap<Integer> getRepresentation() {
		return ValueMap.builder(0)
				.put("x", this.x)
				.put("y", this.y)
				.put("z", this.z)
				.build();
	}
}
