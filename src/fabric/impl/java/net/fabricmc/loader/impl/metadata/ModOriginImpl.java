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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quiltmc.loader.api.ModContainer;

@Deprecated
public final class ModOriginImpl implements net.fabricmc.loader.api.metadata.ModOrigin {
	private final List<Path> originPaths;

	public ModOriginImpl(ModContainer quilt) {
		List<Path> origins = new ArrayList<>();
		List<List<Path>> sourcePaths = quilt.getSourcePaths();
		for (List<Path> list : sourcePaths) {
			if (list.size() == 1) {
				origins.add(list.get(0));
			} else {
				origins = null;
				break;
			}
		}

		this.originPaths = origins == null ? null : Collections.unmodifiableList(origins);
	}

	@Override
	public Kind getKind() {
		return originPaths == null ? Kind.UNKNOWN : Kind.PATH;
	}

	@Override
	public List<Path> getPaths() {
		if (originPaths != null) {
			return originPaths;
		}
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
