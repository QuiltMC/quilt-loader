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

package org.quiltmc.loader.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.VersionIntervalImpl;

/** Representation of a constraint on a version, closed or open.
 * <p>
 * The represented version constraint is contiguous between its lower and upper limit; disjoint intervals are built
 * using collections of {@link VersionInterval}. Empty intervals may be represented by {@code null} or any interval
 * 
 * @code (x,x)} with x being a non-{@code null} version and both endpoints being exclusive. */
@ApiStatus.NonExtendable
public interface VersionInterval extends Comparable<VersionInterval> {
	VersionInterval ALL = new VersionIntervalImpl(null, false, null, false);

	/** @param min the minimum version, or null for negative infinity
	 * @param minInclusive
	 * @param max the maximum version, or null for infinity
	 * @param maxInclusive
	 * @return */
	static VersionInterval of(Version min, boolean minInclusive, Version max, boolean maxInclusive) {
		return new VersionIntervalImpl(min, minInclusive, max, maxInclusive);
	}

	static VersionInterval ofExact(Version version) {
		return new VersionIntervalImpl(version, true, version, true);
	}

	/** Get whether the interval uses {@link Version.Semantic} compatible bounds.
	 *
	 * @return True if both bounds are open (null), {@link Version.Semantic} instances or a combination of both, false
	 *         otherwise. */
	boolean isSemantic();

	/** Get the lower limit of the version interval.
	 *
	 * @return Version's lower limit or null if none, inclusive depending on {@link #isMinInclusive()} */
	@Nullable
	Version getMin();

	/** Get whether the lower limit of the version interval is inclusive, and {@link #getMin()} can equal a version to
	 * satisfy this interval.
	 *
	 * @return True if inclusive, false otherwise */
	boolean isMinInclusive();

	/** Get the upper limit of the version interval.
	 *
	 * @return Version's upper limit or null if none, inclusive depending on {@link #isMaxInclusive()} */
	@Nullable
	Version getMax();

	/** Get whether the upper limit of the version interval is inclusive.
	 *
	 * @return True if inclusive, false otherwise */
	boolean isMaxInclusive();

	default boolean isSatisfiedBy(Version version) {

		if (version.raw().equals("${version}") && QuiltLoader.isDevelopmentEnvironment()) {
			// Special cased by QMJ
			return true;
		}

		Version min = getMin();
		if (min != null) {
			int cmp = min.compareTo(version);
			if (isMinInclusive()) {
				if (cmp > 0) {
					return false;
				}
			} else if (cmp >= 0) {
				return false;
			}
		}

		Version max = getMax();
		if (max != null) {
			int cmp = max.compareTo(version);
			if (isMaxInclusive()) {
				if (cmp < 0) {
					return false;
				}
			} else if (cmp <= 0) {
				return false;
			}
		}

		return true;
	}

	/** Compares this interval to the other one.<br>
	 * If these intervals do not overlap ({@link #doesOverlap(VersionInterval)}) then this is trivial.<br>
	 * If these intervals do overlap then this compares minimums first, and maximums second. */
	@Override
	default int compareTo(VersionInterval o) {
		Version minT = getMin();
		Version minO = o.getMin();
		if ((minT == null) != (minO == null)) {
			return minT == null ? -1 : +1;
		}
		if (minT != null) {
			// Both not null
			int cmp = minT.compareTo(minO);
			if (cmp != 0) {
				return cmp;
			}
			boolean inclusiveT = isMinInclusive();
			boolean inclusiveO = o.isMinInclusive();
			if (inclusiveT != inclusiveO) {
				return inclusiveT ? -1 : +1;
			}
		}
		Version maxT = getMax();
		Version maxO = o.getMax();
		if ((maxT == null) != (maxO == null)) {
			return maxT == null ? +1 : -1;
		}
		if (maxT != null) {
			// Both not null
			int cmp = maxT.compareTo(maxO);
			if (cmp != 0) {
				return cmp;
			}
			boolean inclusiveT = isMaxInclusive();
			boolean inclusiveO = o.isMaxInclusive();
			if (inclusiveT != inclusiveO) {
				return inclusiveT ? +1 : -1;
			}
		}
		return 0;
	}

	default VersionRange toVersionRange() {
		return VersionRange.ofInterval(this);
	}

	default VersionInterval and(VersionInterval o) {
		return and(this, o);
	}

	default VersionRange or(VersionRange o) {
		return or(o, this);
	}

	default VersionRange or(VersionInterval o) {
		return VersionRange.ofIntervals(Arrays.asList(this, o));
	}

	default VersionRange not() {
		return not(this);
	}

	/** Checks to see if this overlaps with the other interval. */
	default boolean doesOverlap(VersionInterval o) {
		return or(o).size() == 1;
	}

	/** Merges this interval with the given one, if they are overlapping. Essentially this computes
	 * {@link #or(VersionInterval)} but for the specific case where the result is a single {@link VersionInterval}.
	 * 
	 * @return The merged interval, which satisfies every version that either this or the given interval does.
	 * @throws IllegalArgumentException if {@link #doesOverlap(VersionInterval)} returns false. */
	default VersionInterval mergeOverlapping(VersionInterval o) {
		VersionRange range = or(o);
		if (range.size() > 1) {
			throw new IllegalArgumentException(this + " doesn't overlap with " + o);
		}
		return range.first();
	}

	/** Compute the intersection between two version intervals. */
	static VersionInterval and(VersionInterval a, VersionInterval b) {
		return VersionIntervalImpl.and(a, b);
	}

	/** Compute the intersection between two potentially disjoint of version intervals. */
	static VersionRange and(VersionRange a, VersionRange b) {
		return VersionRange.ofIntervals(VersionIntervalImpl.and(a, b));
	}

	/** Compute the union between multiple version intervals. */
	static VersionRange or(VersionRange a, VersionInterval b) {
		List<VersionInterval> list = new ArrayList<>();
		list.addAll(a);
		list.add(b);
		return VersionRange.ofIntervals(list);
	}

	static VersionRange not(VersionInterval interval) {
		return VersionIntervalImpl.not(interval);
	}

	static VersionRange not(VersionRange intervals) {
		return VersionIntervalImpl.not(intervals);
	}
}
