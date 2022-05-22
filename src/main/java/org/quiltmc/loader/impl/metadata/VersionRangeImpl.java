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

package org.quiltmc.loader.impl.metadata;

import org.quiltmc.loader.api.version.Version;
import org.quiltmc.loader.api.version.VersionInterval;
import org.quiltmc.loader.api.version.VersionRange;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class VersionRangeImpl extends AbstractCollection<VersionInterval> implements VersionRange {
	public static final VersionRange ANY = new VersionRangeImpl(Collections.singleton(VersionIntervalImpl.ALL));
	public static final VersionRange NONE = new VersionRangeImpl(Collections.emptyList());
	private final Collection<VersionInterval> intervals;
	public VersionRangeImpl(Collection<VersionInterval> intervals) {
		this.intervals = Collections.unmodifiableCollection(intervals);
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
	public boolean satisfiedBy(Version version) {
		for (VersionInterval interval : intervals) {
			if (interval.satisfiedBy(version)) {
				return true;
			}
		}

		return false;
	}
}
