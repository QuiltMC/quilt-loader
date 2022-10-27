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

package org.quiltmc.loader.impl.metadata;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.api.VersionInterval;
import org.quiltmc.loader.api.VersionRange;

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
	@Deprecated
	public Collection<VersionConstraint> convertToConstraints() {
		
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
