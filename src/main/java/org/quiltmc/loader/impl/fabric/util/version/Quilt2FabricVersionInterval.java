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

package org.quiltmc.loader.impl.fabric.util.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.impl.metadata.VersionIntervalImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;

@Deprecated
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class Quilt2FabricVersionInterval implements VersionInterval {
	private final org.quiltmc.loader.api.VersionInterval quilt;

	public Quilt2FabricVersionInterval(Version min, boolean minInclusive,
			Version max, boolean maxInclusive) {
		this.quilt = org.quiltmc.loader.api.VersionInterval.of(Quilt2FabricVersion.fromFabric(min), minInclusive, 
			Quilt2FabricVersion.fromFabric(max), maxInclusive);
	}

	public Quilt2FabricVersionInterval(org.quiltmc.loader.api.VersionInterval quilt) {
		this.quilt = quilt;
	}

	@Override
	public boolean isSemantic() {
		return quilt.isSemantic();
	}

	@Override
	public Version getMin() {
		return Quilt2FabricVersion.toFabric(quilt.getMin());
	}

	@Override
	public boolean isMinInclusive() {
		return quilt.isMinInclusive();
	}

	@Override
	public Version getMax() {
		return Quilt2FabricVersion.toFabric(quilt.getMax());
	}

	@Override
	public boolean isMaxInclusive() {
		return quilt.isMaxInclusive();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Quilt2FabricVersionInterval && quilt.equals(((Quilt2FabricVersionInterval) obj).quilt);
	}

	@Override
	public int hashCode() {
		return quilt.hashCode();
	}

	@Override
	public String toString() {
		return quilt.toString();
	}

	public static VersionInterval and(VersionInterval a, VersionInterval b) {
		if (a == null || b == null) return null;

		return new Quilt2FabricVersionInterval(
			VersionIntervalImpl.and(((Quilt2FabricVersionInterval) a).quilt, ((Quilt2FabricVersionInterval) b).quilt)
		);
	}

	public static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
		VersionRange rangeA = toRange(a);
		VersionRange rangeB = toRange(b);
		VersionRange combined = rangeA.combineMatchingBoth(rangeB);
		List<VersionInterval> intervals = new ArrayList<>();
		for (org.quiltmc.loader.api.VersionInterval quilt : combined) {
			intervals.add(new Quilt2FabricVersionInterval(quilt));
		}
		return intervals;
	}

	private static VersionRange toRange(Collection<VersionInterval> a) {
		List<org.quiltmc.loader.api.VersionInterval> quilt = new ArrayList<>();
		for (VersionInterval fabric : a) {
			quilt.add(((Quilt2FabricVersionInterval) fabric).quilt);
		}
		return VersionRange.ofIntervals(quilt);
	}

	public static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
		if (a.isEmpty()) {
			if (b == null) {
				return Collections.emptyList();
			} else {
				return Collections.singletonList(b);
			}
		}

		List<VersionInterval> ret = new ArrayList<>(a.size() + 1);

		for (VersionInterval v : a) {
			merge(v, ret);
		}

		merge(b, ret);

		return ret;
	}

	private static void merge(VersionInterval a, List<VersionInterval> out) {
		if (a == null) return;

		if (out.isEmpty()) {
			out.add(a);
			return;
		}

		if (out.size() == 1) {
			VersionInterval e = out.get(0);

			if (e.getMin() == null && e.getMax() == null) {
				return;
			}
		}

		if (!a.isSemantic()) {
			mergePlain(a, out);
		} else {
			mergeSemantic(a, out);
		}
	}

	private static void mergePlain(VersionInterval a, List<VersionInterval> out) {
		Version aMin = a.getMin();
		Version aMax = a.getMax();
		Version v = aMin != null ? aMin : aMax;
		assert v != null;

		for (int i = 0; i < out.size(); i++) {
			VersionInterval c = out.get(i);

			if (v.equals(c.getMin())) {
				if (aMin == null) {
					assert aMax.equals(c.getMin());
					out.clear();
					out.add(INFINITE);
				} else if (aMax == null && c.getMax() != null) {
					out.set(i, a);
				}

				return;
			} else if (v.equals(c.getMax())) {
				assert c.getMin() == null;

				if (aMax == null) {
					assert aMin.equals(c.getMax());
					out.clear();
					out.add(INFINITE);
				}

				return;
			}
		}

		out.add(a);
	}

	private static void mergeSemantic(VersionInterval a, List<VersionInterval> out) {
		SemanticVersion aMin = (SemanticVersion) a.getMin();
		SemanticVersion aMax = (SemanticVersion) a.getMax();

		if (aMin == null && aMax == null) {
			out.clear();
			out.add(INFINITE);
			return;
		}

		for (int i = 0; i < out.size(); i++) {
			VersionInterval c = out.get(i);
			if (!c.isSemantic()) continue;

			SemanticVersion cMin = (SemanticVersion) c.getMin();
			SemanticVersion cMax = (SemanticVersion) c.getMax();
			int cmp;

			if (aMin == null) { // ..a..]
				if (cMax == null) { // ..a..] [..c..
					cmp = aMax.compareTo((Version) cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c.. or ..a..)(..c..
						out.add(i, a);
					} else { // ..a..|..c.. or ..a.[..].c..
						out.clear();
						out.add(INFINITE);
					}

					return;
				} else { // ..a..] [..c..]
					cmp = compareMax(a, c);

					if (cmp >= 0) { // a encompasses c
						out.remove(i);
						i--;
					} else if (cMin == null) { // c encompasses a
						return;
					} else { // aMax < cMax
						cmp = aMax.compareTo((Version) cMin);

						if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c..] or ..a..)(..c..]
							out.add(i, a);
						} else { // c extends a to the right
							out.set(i, new Quilt2FabricVersionInterval(null, false, cMax, c.isMaxInclusive()));
						}

						return;
					}
				}
			} else if (cMax == null) { // [..c..
				cmp = compareMin(a, c);

				if (cmp >= 0) { // c encompasses a
					// no-op
				} else if (aMax == null) { // a encompasses c
					while (out.size() > i) out.remove(i);
					out.add(a);
				} else { // aMin < cMin
					cmp = aMax.compareTo((Version) cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // [..a..]..[..c.. or [..a..)(..c..
						out.add(i, a);
					} else { // a extends c to the left
						out.set(i, new Quilt2FabricVersionInterval(aMin, a.isMinInclusive(), null, false));
					}
				}

				return;
			} else if ((cmp = aMin.compareTo((Version) cMax)) < 0 || cmp == 0 && (a.isMinInclusive() || c.isMaxInclusive())) {
				int cmp2;

				if (aMax == null || cMin == null || (cmp2 = aMax.compareTo((Version) cMin)) > 0 || cmp2 == 0 && (a.isMaxInclusive() || c.isMinInclusive())) {
					int cmpMin = compareMin(a, c);
					int cmpMax = compareMax(a, c);

					if (cmpMax <= 0) { // aMax <= cMax
						if (cmpMin < 0) { // aMin < cMin
							out.set(i, new Quilt2FabricVersionInterval(aMin, a.isMinInclusive(), cMax, c.isMaxInclusive()));
						}

						return;
					} else if (cmpMin > 0) { // aMin > cMin, aMax > cMax
						a = new Quilt2FabricVersionInterval(cMin, c.isMinInclusive(), aMax, a.isMaxInclusive());
					}

					out.remove(i);
					i--;
				} else {
					out.add(i, a);
					return;
				}
			}
		}

		out.add(a);
	}

	private static int compareMin(VersionInterval a, VersionInterval b) {
		SemanticVersion aMin = (SemanticVersion) a.getMin();
		SemanticVersion bMin = (SemanticVersion) b.getMin();
		int cmp;

		if (aMin == null) { // a <= b
			if (bMin == null) { // a == b == -inf
				return 0;
			} else { // bMin != null -> a < b
				return -1;
			}
		} else if (bMin == null || (cmp = aMin.compareTo((Version) bMin)) > 0 || cmp == 0 && !a.isMinInclusive() && b.isMinInclusive()) { // a > b
			return 1;
		} else if (cmp < 0 || a.isMinInclusive() && !b.isMinInclusive()) { // a < b
			return -1;
		} else { // cmp == 0 && a.minInclusive() == b.minInclusive() -> a == b
			return 0;
		}
	}

	private static int compareMax(VersionInterval a, VersionInterval b) {
		SemanticVersion aMax = (SemanticVersion) a.getMax();
		SemanticVersion bMax = (SemanticVersion) b.getMax();
		int cmp;

		if (aMax == null) { // a >= b
			if (bMax == null) { // a == b == inf
				return 0;
			} else { // bMax != null -> a > b
				return 1;
			}
		} else if (bMax == null || (cmp = aMax.compareTo((Version) bMax)) < 0 || cmp == 0 && !a.isMaxInclusive() && b.isMaxInclusive()) { // a < b
			return -1;
		} else if (cmp > 0 || a.isMaxInclusive() && !b.isMaxInclusive()) { // a > b
			return 1;
		} else { // cmp == 0 && a.maxInclusive() == b.maxInclusive() -> a == b
			return 0;
		}
	}

	public static List<VersionInterval> not(VersionInterval interval) {
		if (interval == null) { // () = empty interval -> infinite
			return Collections.singletonList(INFINITE);
		} else if (interval.getMin() == null) { // (-∞, = at least half-open towards min
			if (interval.getMax() == null) { // (-∞,∞) = infinite -> empty
				return Collections.emptyList();
			} else { // (-∞,x = left open towards min -> half open towards max
				return Collections.singletonList(new Quilt2FabricVersionInterval(interval.getMax(), !interval.isMaxInclusive(), null, false));
			}
		} else if (interval.getMax() == null) { // x,∞) = half open towards max -> half open towards min
			return Collections.singletonList(new Quilt2FabricVersionInterval(null, false, interval.getMin(), !interval.isMinInclusive()));
		} else if (interval.getMin().equals(interval.getMax()) && !interval.isMinInclusive() && !interval.isMaxInclusive()) { // (x,x) = effectively empty interval -> infinite
			return Collections.singletonList(INFINITE);
		} else { // closed interval -> 2 half open intervals on each side
			List<VersionInterval> ret = new ArrayList<>(2);
			ret.add(new Quilt2FabricVersionInterval(null, false, interval.getMin(), !interval.isMinInclusive()));
			ret.add(new Quilt2FabricVersionInterval(interval.getMax(), !interval.isMaxInclusive(), null, false));

			return ret;
		}
	}

	public static List<VersionInterval> not(Collection<VersionInterval> intervals) {
		if (intervals.isEmpty()) return Collections.singletonList(INFINITE);
		if (intervals.size() == 1) return not(intervals.iterator().next());

		// !(i0 || i1 || i2) == !i0 && !i1 && !i2

		List<VersionInterval> ret = null;

		for (VersionInterval v : intervals) {
			List<VersionInterval> inverted = not(v);

			if (ret == null) {
				ret = inverted;
			} else {
				ret = and(ret, inverted);
			}

			if (ret.isEmpty()) break;
		}

		return ret;
	}
}
