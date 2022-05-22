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

package org.quiltmc.loader.api.version;

import org.quiltmc.loader.impl.metadata.VersionRangeImpl;

import java.util.Collection;

/**
 * A collection of {@link VersionInterval}s, which are part of one range of versions.
 * For example, a range of versions could be 2.0.0 < x < 3.0.0 OR 4.0.0 < x < 5.0.0
 */
public interface VersionRange extends Collection<VersionInterval> {
	static VersionRange of(VersionInterval interval) {
		return new VersionRangeImpl(interval);
	}

	static VersionRange of(Collection<VersionInterval> collection) {
		return new VersionRangeImpl(collection);
	}

	static VersionRange ofExact(Version version) {
		return new VersionRangeImpl(VersionInterval.ofExact(version));
	}

	static VersionRange of(Version min, boolean minInlcusive, Version max, boolean maxInclusive) {
		return new VersionRangeImpl(VersionInterval.of(min, minInlcusive, max, maxInclusive));
	}


	VersionRange NONE = VersionRangeImpl.NONE;
	VersionRange ANY = VersionRangeImpl.ANY;
	boolean satisfiedBy(Version version);
}

