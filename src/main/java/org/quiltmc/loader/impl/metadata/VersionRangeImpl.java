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

package org.quiltmc.loader.impl.metadata;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.api.VersionConstraint.Type;
import org.quiltmc.loader.api.VersionInterval;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.impl.metadata.qmj.VersionConstraintImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class VersionRangeImpl extends AbstractSet<VersionInterval> implements VersionRange {
	public static final VersionRangeImpl ANY = new VersionRangeImpl(Collections.singleton(VersionIntervalImpl.ALL));
	public static final VersionRangeImpl NONE = new VersionRangeImpl(Collections.emptyList());
	private final SortedSet<VersionInterval> intervals;

	public VersionRangeImpl(Collection<VersionInterval> intervals) {
		VersionInterval[] array = intervals.toArray(new VersionInterval[0]);
		if (array.length == 0) {
			// No reason not to share
			this.intervals = NONE == null ? new TreeSet<>() : NONE.intervals;
		} else if (array.length == 1) {
			this.intervals = new TreeSet<>();
			this.intervals.add(array[0]);
		} else {
			Arrays.sort(array);
			this.intervals = new TreeSet<>();

			VersionInterval last = array[0];
			for (int i = 1; i < array.length; i++) {
				VersionInterval next = array[i];
				Version nextMin = next.getMin();
				Version lastMax = last.getMax();
				if (lastMax == null) {
					// Upper is the entire bound, no point merging since it already contains everything
					break;
				}
				if (nextMin == null || nextMin.compareTo(lastMax) < 0) {
					Version nextMax = next.getMax();
					if (nextMax == null) {
						last = VersionInterval.of(last.getMin(), last.isMinInclusive(), null, false);
						break;
					}
					int cmp = nextMax.compareTo(lastMax);
					final Version max;
					final boolean maxInclusive;
					if (cmp == 0) {
						max = lastMax;
						maxInclusive = next.isMaxInclusive() || last.isMaxInclusive();
					} else {
						max = cmp < 0 ? nextMax : lastMax;
						maxInclusive = (cmp < 0 ? next : last).isMaxInclusive();
					}
					last = VersionInterval.of(last.getMin(), last.isMinInclusive(), max, maxInclusive);
				} else {
					// They don't overlap
					this.intervals.add(last);
					last = next;
				}
			}

			this.intervals.add(last);
		}
	}

	public VersionRangeImpl(VersionInterval interval) {
		this(Collections.singleton(interval));
	}

	@Override
	public Iterator<VersionInterval> iterator() {
		return intervals.iterator();
	}

	@Override
	public int size() {
		return intervals.size();
	}

	@Override
	public Comparator<? super VersionInterval> comparator() {
		return null;
	}

	@Override
	public String toString() {
		if (this.size() == 1) {
			return this.intervals.first().toString();
		} else if (this.isEmpty()) {
			return "{ <empty set> }";
		} else {
			StringBuilder sb = new StringBuilder("{ ");
			Iterator<VersionInterval> iter = this.iterator();
			sb.append(iter.next());

			while (iter.hasNext()) {
				sb.append(" ∪ "); //TODO: there is probably a more logical way of notating this, but this is what i learned.
				sb.append(iter.next());
			}

			sb.append(" }");

			return sb.toString();
		}
	}

	@Override
	public VersionRange combineMatchingBoth(VersionRange other) {
		List<VersionInterval> combined = new ArrayList<>();
		for (VersionInterval a : this) {
			for (VersionInterval b : other) {
				VersionInterval merged = a.and(b);
				if (merged != null) {
					combined.add(merged);
				}
			}
		}
		return new VersionRangeImpl(combined);
	}

	@Override
	@Deprecated
	public Collection<VersionConstraint> convertToConstraints() {
		List<VersionConstraint> constraints = new ArrayList<>();
		for (VersionInterval interval : this) {
			Version min = interval.getMin();
			boolean minInclusive = interval.isMaxInclusive();
			Version max = interval.getMax();
			boolean maxInclusive = interval.isMaxInclusive();
			if (min == null && max == null) {
				constraints.add(VersionConstraint.any());
				continue;
			}

			if (Objects.equals(min, max) && maxInclusive && minInclusive) {
				constraints.add(new VersionConstraintImpl(min, VersionConstraint.Type.EQUALS));
				continue;
			}

			VersionConstraint.Type maxBound = maxInclusive
				? VersionConstraint.Type.LESSER_THAN_OR_EQUAL
				: VersionConstraint.Type.LESSER_THAN;

			if (min == null) {
				constraints.add(new VersionConstraintImpl(max, maxBound));
				continue;
			}

			VersionConstraint.Type minBound = minInclusive
				? VersionConstraint.Type.GREATER_THAN_OR_EQUAL
				: VersionConstraint.Type.GREATER_THAN;

			if (max == null) {
				constraints.add(new VersionConstraintImpl(min, minBound));
				continue;
			}

			// TODO: Check for the major/minor types!
			constraints.add(new VersionConstraintImpl(min, minBound));
			constraints.add(new VersionConstraintImpl(max, maxBound));
		}
		return constraints;
	}

	@Override
	public VersionRangeImpl subSet(VersionInterval fromElement, VersionInterval toElement) {
		return new VersionRangeImpl(intervals.subSet(fromElement, toElement));
	}

	@Override
	public VersionRangeImpl headSet(VersionInterval toElement) {
		return new VersionRangeImpl(intervals.headSet(toElement));
	}

	@Override
	public VersionRangeImpl tailSet(VersionInterval fromElement) {
		return new VersionRangeImpl(intervals.tailSet(fromElement));
	}

	@Override
	public VersionInterval first() {
		return intervals.first();
	}

	@Override
	public VersionInterval last() {
		return intervals.last();
	}
}
