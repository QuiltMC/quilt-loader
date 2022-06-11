/*
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

package net.fabricmc.loader.impl.metadata;

import java.nio.file.Path;
import java.util.List;

import org.quiltmc.loader.api.ModContainer;

@Deprecated
public final class ModOriginImpl implements net.fabricmc.loader.api.metadata.ModOrigin {
	private final ModContainer mod;

	public ModOriginImpl(ModContainer quilt) {
		this.mod = quilt;
	}

	@Override
	public Kind getKind() {
		return Kind.UNKNOWN;
	}

	@Override
	public List<Path> getPaths() {
		throw new UnsupportedOperationException("getPaths() Not supported for Kind.UNKNOWN");
	}

	@Override
	public String getParentModId() {
		throw new UnsupportedOperationException("getParentModId() Not supported for Kind.UNKNOWN");
	}

	@Override
	public String getParentSubLocation() {
		throw new UnsupportedOperationException("getParentSubLocation() Not supported for Kind.UNKNOWN");
	}
}
