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

package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.metadata.VersionRangeImpl;

import java.util.Collection;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * A collection of {@link VersionInterval}s, which are part of one range of versions.
 * For example, a range of versions could be 2.0.0 < x < 3.0.0 OR 4.0.0 < x < 5.0.0.
 * For simplicities sake this is always sorted, and never contains overlapping intervals.
 */
@ApiStatus.NonExtendable
public interface VersionRange extends SortedSet<VersionInterval> {

	static VersionRange ofInterval(VersionInterval interval) {
		if (interval.getMin() == null && interval.getMax() == null) {
			return ANY;
		}
		return new VersionRangeImpl(interval);
	}

	static VersionRange ofIntervals(Collection<VersionInterval> collection) {
		if (collection.isEmpty()) {
			return NONE;
		}
		return new VersionRangeImpl(collection);
	}

	static VersionRange ofRanges(Collection<VersionRange> collection) {
		return new VersionRangeImpl(collection.stream().flatMap(Collection::stream).collect(Collectors.toList()));
	}
	static VersionRange ofExact(Version version) {
		return new VersionRangeImpl(VersionInterval.ofExact(version));
	}

	static VersionRange ofInterval(Version min, boolean minInlcusive, Version max, boolean maxInclusive) {
		return new VersionRangeImpl(VersionInterval.of(min, minInlcusive, max, maxInclusive));
	}

	VersionRange NONE = VersionRangeImpl.NONE;

	VersionRange ANY = VersionRangeImpl.ANY;

	default boolean isSatisfiedBy(Version version) {
		for (VersionInterval interval : this) {
			if (interval.isSatisfiedBy(version)) {
				return true;
			}
		}

		return false;
	}

	/** @return A {@link VersionRange} that only matches versions which match both this range and the given range. */
	VersionRange combineMatchingBoth(VersionRange other);

	/** Converts this range into the deprecated {@link VersionConstraint} api. */
	@Deprecated
	Collection<VersionConstraint> convertToConstraints();

	// SortedSet overrides

	@Override
	VersionRange subSet(VersionInterval fromElement, VersionInterval toElement);

	@Override
	VersionRange headSet(VersionInterval toElement);

	@Override
	VersionRange tailSet(VersionInterval fromElement);
}
