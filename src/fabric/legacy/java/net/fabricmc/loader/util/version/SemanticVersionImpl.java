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

package net.fabricmc.loader.util.version;

import java.util.Optional;

import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.impl.fabric.util.version.Quilt2FabricSemanticVersion;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public class SemanticVersionImpl extends Quilt2FabricSemanticVersion implements SemanticVersion {
	private final SemanticVersion parent;

	protected SemanticVersionImpl() {
		super(null);
		parent = null;
	}

	public SemanticVersionImpl(String version, boolean storeX) throws VersionParsingException {
		super(parseQuilt(version));
		parent = null;
	}

	private static org.quiltmc.loader.api.Version.Semantic parseQuilt(String version) throws VersionParsingException {
		try {
			return org.quiltmc.loader.api.Version.Semantic.of(version);
		} catch (VersionFormatException e) {
			throw new VersionParsingException(e);
		}
	}

	public SemanticVersionImpl(org.quiltmc.loader.api.Version.Semantic quilt) {
		super(quilt);
		this.parent = null;
	}

	public SemanticVersion getParent() {
		return parent;
	}

	@Override
	public int getVersionComponentCount() {
		return parent == null ? super.getVersionComponentCount() : parent.getVersionComponentCount();
	}

	@Override
	public int getVersionComponent(int pos) {
		return parent == null ? super.getVersionComponent(pos) : parent.getVersionComponent(pos);
	}

	@Override
	public Optional<String> getPrereleaseKey() {
		return parent == null ? super.getPrereleaseKey() : parent.getPrereleaseKey();
	}

	@Override
	public Optional<String> getBuildKey() {
		return parent == null ? super.getBuildKey() : parent.getBuildKey();
	}

	@Override
	public String getFriendlyString() {
		return parent == null ? super.getFriendlyString() : parent.getFriendlyString();
	}

	@Override
	public boolean equals(Object o) {
		return parent == null ? super.equals(o) : parent.equals(o);
	}

	@Override
	public int hashCode() {
		return parent == null ? super.hashCode() : parent.hashCode();
	}

	@Override
	public String toString() {
		return parent == null ? super.toString() : parent.toString();
	}

	@Override
	public boolean hasWildcard() {
		return parent == null ? super.hasWildcard() : parent.hasWildcard();
	}

	public boolean equalsComponentsExactly(SemanticVersionImpl other) {
		for (int i = 0; i < Math.max(getVersionComponentCount(), other.getVersionComponentCount()); i++) {
			if (getVersionComponent(i) != other.getVersionComponent(i)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public int compareTo(Version o) {
		return parent == null ? super.compareTo(o) : parent.compareTo(o);
	}
}
