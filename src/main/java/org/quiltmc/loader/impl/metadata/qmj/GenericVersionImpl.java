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

import org.quiltmc.loader.api.Version;

public class GenericVersionImpl implements Version.Raw {
	private final String raw;

	public GenericVersionImpl(String raw) {
		this.raw = raw;
	}

	@Override
	public String raw() {
		return raw;
	}

	@Override
	public String toString() {
		return raw;
	}

	@Override
	public int hashCode() {
		return raw.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Version.Raw) {
			return raw.equals(((Version.Raw) obj).raw());
		} else {
			return false;
		}
	}

	@Override
	public int compareTo(Version other) {
		return compareRaw(raw(), other.raw());
	}

	public static int compareRaw(String a, String b) {
		// Ideally this would use number-aware comparison
		// Oh well
		return a.compareTo(b);
	}
}
