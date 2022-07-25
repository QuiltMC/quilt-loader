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

package org.quiltmc.loader.api.version;


import org.quiltmc.loader.impl.metadata.VersionIntervalImpl;

/**
 * Representation of a constraint on a version, closed or open.
 *
 * <p>The represented version constraint is contiguous between its lower and upper limit; disjoint intervals are built
 * using collections of {@link VersionInterval}. Empty intervals may be represented by {@code null} or any interval
 * @code (x,x)} with x being a non-{@code null} version and both endpoints being exclusive.
 */
public interface VersionInterval {
	VersionInterval ALL = new VersionIntervalImpl(null, false, null, false);

	/**
	 * @param min the minimum version, or null for negative infinity
	 * @param minInclusive
	 * @param max the maximum version, or null for infinity
	 * @param maxInclusive
	 * @return
	 */
	static VersionInterval of(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
		return new VersionIntervalImpl(min, minInclusive, max, maxInclusive);
	}

	static VersionInterval ofExact(Version version) {
		return new VersionIntervalImpl(version, true, version, true);
	}
	/**
	 * Get whether the interval uses {@link Version.Semantic} compatible bounds.
	 *
	 * @return True if both bounds are open (null), {@link Version.Semantic} instances or a combination of both, false otherwise.
	 */
	boolean isSemantic();

	/**
	 * Get the lower limit of the version interval.
	 *
	 * @return Version's lower limit or null if none, inclusive depending on {@link #isMinInclusive()}
	 */
	Version getMin();

	/**
	 * Get whether the lower limit of the version interval is inclusive.
	 *
	 * @return True if inclusive, false otherwise
	 */
	boolean isMinInclusive();

	/**
	 * Get the upper limit of the version interval.
	 *
	 * @return Version's upper limit or null if none, inclusive depending on {@link #isMaxInclusive()}
	 */
	Version getMax();

	/**
	 * Get whether the upper limit of the version interval is inclusive.
	 *
	 * @return True if inclusive, false otherwise
	 */
	boolean isMaxInclusive();

	boolean satisfiedBy(Version version);
	default VersionRange toVersionRange() {
		return VersionRange.ofInterval(this);
	}
	default VersionInterval and(VersionInterval o) {
		return and(this, o);
	}

	default VersionRange or(VersionRange o) {
		return or(o, this);
	}

	default VersionRange not() {
		return not(this);
	}

	/**
	 * Compute the intersection between two version intervals.
	 */
	static VersionInterval and(VersionInterval a, VersionInterval b) {
		return VersionIntervalImpl.and(a, b);
	}

	/**
	 * Compute the intersection between two potentially disjoint of version intervals.
	 */
	static VersionRange and(VersionRange a, VersionRange b) {
		return VersionRange.ofIntervals(VersionIntervalImpl.and(a, b));
	}

	/**
	 * Compute the union between multiple version intervals.
	 */
	static VersionRange or(VersionRange a, VersionInterval b) {
		return VersionRange.ofIntervals(VersionIntervalImpl.or(a, b));
	}

	static VersionRange not(VersionInterval interval) {
		return VersionRange.ofIntervals(VersionIntervalImpl.not(interval));
	}

	static VersionRange not(VersionRange intervals) {
		return VersionRange.ofIntervals(VersionIntervalImpl.not(intervals));
	}
}
