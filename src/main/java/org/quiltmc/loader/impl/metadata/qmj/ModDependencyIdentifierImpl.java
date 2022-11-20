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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Objects;

import org.quiltmc.loader.api.ModDependencyIdentifier;

public final class ModDependencyIdentifierImpl implements ModDependencyIdentifier {
	private final String mavenGroup;
	private final String id;

	public ModDependencyIdentifierImpl(String raw) {
		int split = raw.indexOf(":");
		if (split > 0) {
			mavenGroup = raw.substring(0, split);
			id = raw.substring(split + 1);
		} else {
			mavenGroup = "";
			id = raw;
		}
	}

	public ModDependencyIdentifierImpl(String mavenGroup, String id) {
		this.mavenGroup = mavenGroup;
		this.id = id;
	}

	@Override
	public String mavenGroup() {
		return mavenGroup;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String toString() {
		if (!this.mavenGroup.isEmpty()) {
			return this.mavenGroup + ":" + this.id;
		}

		return this.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, mavenGroup);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		ModDependencyIdentifierImpl other = (ModDependencyIdentifierImpl) obj;
		return Objects.equals(id, other.id) && Objects.equals(mavenGroup, other.mavenGroup);
	}
}
