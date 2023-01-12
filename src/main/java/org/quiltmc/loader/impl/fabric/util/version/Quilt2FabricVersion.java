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

package org.quiltmc.loader.impl.fabric.util.version;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.util.version.SemanticVersionImpl;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class Quilt2FabricVersion implements net.fabricmc.loader.api.Version {

	final org.quiltmc.loader.api.Version quilt;

	Quilt2FabricVersion(org.quiltmc.loader.api.Version quilt) {
		this.quilt = quilt;
	}

	public static net.fabricmc.loader.api.Version toFabric(org.quiltmc.loader.api.Version quilt) {
		if (quilt == null) {
			return null;
		} else if (quilt.isSemantic()) {
			return new Quilt2FabricSemanticVersion(quilt.semantic());
		} else {
			return new Quilt2FabricVersion(quilt);
		}
	}

	public static org.quiltmc.loader.api.Version fromFabric(net.fabricmc.loader.api.Version from) {
		if (from == null) {
			return null;
		} else if (from instanceof Quilt2FabricVersion) {
			return ((Quilt2FabricVersion) from).quilt;
		} else if (from instanceof SemanticVersion) {
			return Quilt2FabricSemanticVersion.fromFabric((SemanticVersion) from);
		} else {
			throw new IllegalStateException("Unexpected version " + from.getClass());
		}
	}

	@Override
	public int compareTo(net.fabricmc.loader.api.Version o) {
		return quilt.compareTo(fromFabric(o));
	}

	@Override
	public String getFriendlyString() {
		return quilt.raw();
	}

	@Override
	public String toString() {
		return quilt.raw();
	}
}
