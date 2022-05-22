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

import org.quiltmc.loader.api.version.Version;
import org.quiltmc.loader.api.version.VersionInterval;
import org.quiltmc.loader.api.version.VersionRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VersionIntervalImpl implements VersionInterval {
	private final Version min;
	private final boolean minInclusive;
	private final Version max;
	private final boolean maxInclusive;

	public VersionIntervalImpl(Version min, boolean minInclusive,
							   Version max, boolean maxInclusive) {
		this.min = min;
		this.minInclusive = min != null && minInclusive;
		this.max = max;
		this.maxInclusive = max != null && maxInclusive;

		assert min != null || !minInclusive;
		assert max != null || !maxInclusive;
		assert min == null || min instanceof Version.Semantic || minInclusive;
		assert max == null || max instanceof Version.Semantic || maxInclusive;
		assert min == null || max == null || min instanceof Version.Semantic && max instanceof Version.Semantic || min.equals(max);
	}

	@Override
	public boolean isSemantic() {
		return (min == null || min instanceof Version.Semantic)
				&& (max == null || max instanceof Version.Semantic);
	}

	@Override
	public Version getMin() {
		return min;
	}

	@Override
	public boolean isMinInclusive() {
		return minInclusive;
	}

	@Override
	public Version getMax() {
		return max;
	}

	@Override
	public boolean isMaxInclusive() {
		return maxInclusive;
	}

	@Override
	public boolean satisfiedBy(Version version) {
		// Since i'm tired and just want to make this work, this method hackily re-uses other code

		boolean satisfied;
		VersionInterval exact = VersionInterval.ofExact(version);
		// Check that the provided version is greater than our minimum
		satisfied = minInclusive ? compareMin(exact, this) >= 0 : compareMin(exact, this) > 0;
		// Check that the provided version is less than our maximum
		satisfied = satisfied && maxInclusive ? compareMax(exact, this) <= 0 : compareMax(exact, this) < 0;

		return satisfied;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof VersionInterval) {
			VersionInterval o = (VersionInterval) obj;

			return Objects.equals(min, o.getMin()) && minInclusive == o.isMinInclusive()
					&& Objects.equals(max, o.getMax()) && maxInclusive == o.isMaxInclusive();
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return (Objects.hashCode(min) + (minInclusive ? 1 : 0)) * 31
				+ (Objects.hashCode(max) + (maxInclusive ? 1 : 0)) * 31;
	}

	@Override
	public String toString() {
		if (min == null) {
			if (max == null) {
				return "(-∞,∞)";
			} else {
				return String.format("(-∞,%s%c", max, maxInclusive ? ']' : ')');
			}
		} else if (max == null) {
			return String.format("%c%s,∞)", minInclusive ? '[' : '(', min);
		} else {
			return String.format("%c%s,%s%c", minInclusive ? '[' : '(', min, max, maxInclusive ? ']' : ')');
		}
	}

	public static VersionInterval and(VersionInterval a, VersionInterval b) {
		if (a == null || b == null) return null;

		if (!a.isSemantic() || !b.isSemantic()) {
			return andPlain(a, b);
		} else {
			return andSemantic(a, b);
		}
	}

	private static VersionInterval andPlain(VersionInterval a, VersionInterval b) {
		Version aMin = a.getMin();
		Version aMax = a.getMax();
		Version bMin = b.getMin();
		Version bMax = b.getMax();

		if (aMin != null) { // -> min must be aMin or invalid
			if (bMin != null && !aMin.equals(bMin) || bMax != null && !aMin.equals(bMax)) {
				return null;
			}

			if (aMax != null || bMax == null) {
				assert Objects.equals(aMax, bMax) || bMax == null;
				return a;
			} else {
				return new VersionIntervalImpl(aMin, true, bMax, b.isMaxInclusive());
			}
		} else if (aMax != null) { // -> min must be bMin, max must be aMax or invalid
			if (bMin != null && !aMax.equals(bMin) || bMax != null && !aMax.equals(bMax)) {
				return null;
			}

			if (bMin == null) {
				return a;
			} else if (bMax != null) {
				return b;
			} else {
				return new VersionIntervalImpl(bMin, true, aMax, true);
			}
		} else {
			return b;
		}
	}

	private static VersionInterval andSemantic(VersionInterval a, VersionInterval b) {
		int minCmp = compareMin(a, b);
		int maxCmp = compareMax(a, b);

		if (minCmp == 0) { // aMin == bMin
			if (maxCmp == 0) { // aMax == bMax -> a == b -> a/b
				return a;
			} else { // aMax != bMax -> a/b..min(a,b)
				return maxCmp < 0 ? a : b;
			}
		} else if (maxCmp == 0) { // aMax == bMax, aMin != bMin -> max(a,b)..a/b
			return minCmp < 0 ? b : a;
		} else if (minCmp < 0) { // aMin < bMin, aMax != bMax -> b..min(a,b)
			if (maxCmp > 0) return b; // a > b -> b

			Version.Semantic aMax = (Version.Semantic) a.getMax();
			Version.Semantic bMin = (Version.Semantic) b.getMin();
			int cmp = bMin.compareTo(aMax);

			if (cmp < 0 || cmp == 0 && b.isMinInclusive() && a.isMaxInclusive()) {
				return new VersionIntervalImpl(bMin, b.isMinInclusive(), aMax, a.isMaxInclusive());
			} else {
				return null;
			}
		} else { // aMin > bMin, aMax != bMax -> a..min(a,b)
			if (maxCmp < 0) return a; // a < b -> a

			Version.Semantic aMin = (Version.Semantic) a.getMin();
			Version.Semantic bMax = (Version.Semantic) b.getMax();
			int cmp = aMin.compareTo(bMax);

			if (cmp < 0 || cmp == 0 && a.isMinInclusive() && b.isMaxInclusive()) {
				return new VersionIntervalImpl(aMin, a.isMinInclusive(), bMax, b.isMaxInclusive());
			} else {
				return null;
			}
		}
	}

	public static VersionRange and(VersionRange a, VersionRange b) {
		if (a.isEmpty() || b.isEmpty()) return VersionRange.NONE;

		if (a.size() == 1 && b.size() == 1) {
			VersionInterval merged = and(a.iterator().next(), b.iterator().next());
			return merged != null ? VersionRange.of(merged) : VersionRange.NONE;
		}

		// (a0 || a1 || a2) && (b0 || b1 || b2) == a0 && b0 && b1 && b2 || a1 && b0 && b1 && b2 || a2 && b0 && b1 && b2

		List<VersionInterval> allMerged = new ArrayList<>();

		for (VersionInterval intervalA : a) {
			for (VersionInterval intervalB : b) {
				VersionInterval merged = and(intervalA, intervalB);
				if (merged != null) allMerged.add(merged);
			}
		}

		if (allMerged.isEmpty()) return VersionRange.NONE;
		if (allMerged.size() == 1) return VersionRange.of(allMerged);

		List<VersionInterval> ret = new ArrayList<>(allMerged.size());

		for (VersionInterval v : allMerged) {
			merge(v, ret);
		}

		return VersionRange.of(ret);
	}

	public static VersionRange or(VersionRange a, VersionInterval b) {
		if (a.isEmpty()) {
			if (b == null) {
				return VersionRange.NONE;
			} else {
				return VersionRange.of(b);
			}
		}

		List<VersionInterval> ret = new ArrayList<>(a.size() + 1);

		for (VersionInterval v : a) {
			merge(v, ret);
		}

		merge(b, ret);

		return VersionRange.of(ret);
	}

	private static void merge(VersionInterval a, List<VersionInterval> out) {
		if (a == null) return;

		if (out.isEmpty()) {
			out.add(a);
			return;
		}

		if (out.size() == 1) {
			VersionInterval e = out.iterator().next();

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
					out.add(ALL);
				} else if (aMax == null && c.getMax() != null) {
					out.set(i, a);
				}

				return;
			} else if (v.equals(c.getMax())) {
				assert c.getMin() == null;

				if (aMax == null) {
					assert aMin.equals(c.getMax());
					out.clear();
					out.add(ALL);
				}

				return;
			}
		}

		out.add(a);
	}

	private static void mergeSemantic(VersionInterval a, List<VersionInterval> out) {
		Version.Semantic aMin = (Version.Semantic) a.getMin();
		Version.Semantic aMax = (Version.Semantic) a.getMax();

		if (aMin == null && aMax == null) {
			out.clear();
			out.add(ALL);
			return;
		}

		for (int i = 0; i < out.size(); i++) {
			VersionInterval c = out.get(i);
			if (!c.isSemantic()) continue;

			Version.Semantic cMin = (Version.Semantic) c.getMin();
			Version.Semantic cMax = (Version.Semantic) c.getMax();
			int cmp;

			if (aMin == null) { // ..a..]
				if (cMax == null) { // ..a..] [..c..
					cmp = aMax.compareTo(cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c.. or ..a..)(..c..
						out.add(i, a);
					} else { // ..a..|..c.. or ..a.[..].c..
						out.clear();
						out.add(ALL);
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
						cmp = aMax.compareTo(cMin);

						if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // ..a..]..[..c..] or ..a..)(..c..]
							out.add(i, a);
						} else { // c extends a to the right
							out.set(i, new VersionIntervalImpl(null, false, cMax, c.isMaxInclusive()));
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
					cmp = aMax.compareTo(cMin);

					if (cmp < 0 || cmp == 0 && !a.isMaxInclusive() && !c.isMinInclusive()) { // [..a..]..[..c.. or [..a..)(..c..
						out.add(i, a);
					} else { // a extends c to the left
						out.set(i, new VersionIntervalImpl(aMin, a.isMinInclusive(), null, false));
					}
				}

				return;
			} else if ((cmp = aMin.compareTo(cMax)) < 0 || cmp == 0 && (a.isMinInclusive() || c.isMaxInclusive())) {
				int cmp2;

				if (aMax == null || cMin == null || (cmp2 = aMax.compareTo(cMin)) > 0 || cmp2 == 0 && (a.isMaxInclusive() || c.isMinInclusive())) {
					int cmpMin = compareMin(a, c);
					int cmpMax = compareMax(a, c);

					if (cmpMax <= 0) { // aMax <= cMax
						if (cmpMin < 0) { // aMin < cMin
							out.set(i, new VersionIntervalImpl(aMin, a.isMinInclusive(), cMax, c.isMaxInclusive()));
						}

						return;
					} else if (cmpMin > 0) { // aMin > cMin, aMax > cMax
						a = new VersionIntervalImpl(cMin, c.isMinInclusive(), aMax, a.isMaxInclusive());
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
		Version.Semantic aMin = (Version.Semantic) a.getMin();
		Version.Semantic bMin = (Version.Semantic) b.getMin();
		int cmp;

		if (aMin == null) { // a <= b
			if (bMin == null) { // a == b == -inf
				return 0;
			} else { // bMin != null -> a < b
				return -1;
			}
		} else if (bMin == null || (cmp = aMin.compareTo(bMin)) > 0 || cmp == 0 && !a.isMinInclusive() && b.isMinInclusive()) { // a > b
			return 1;
		} else if (cmp < 0 || a.isMinInclusive() && !b.isMinInclusive()) { // a < b
			return -1;
		} else { // cmp == 0 && a.minInclusive() == b.minInclusive() -> a == b
			return 0;
		}
	}

	private static int compareMax(VersionInterval a, VersionInterval b) {
		Version.Semantic aMax = (Version.Semantic) a.getMax();
		Version.Semantic bMax = (Version.Semantic) b.getMax();
		int cmp;

		if (aMax == null) { // a >= b
			if (bMax == null) { // a == b == inf
				return 0;
			} else { // bMax != null -> a > b
				return 1;
			}
		} else if (bMax == null || (cmp = aMax.compareTo(bMax)) < 0 || cmp == 0 && !a.isMaxInclusive() && b.isMaxInclusive()) { // a < b
			return -1;
		} else if (cmp > 0 || a.isMaxInclusive() && !b.isMaxInclusive()) { // a > b
			return 1;
		} else { // cmp == 0 && a.maxInclusive() == b.maxInclusive() -> a == b
			return 0;
		}
	}


	public static VersionRange not(VersionInterval interval) {
		if (interval == null) {
			// () = empty interval -> infinite
			return VersionRange.ANY;
		} else if (interval.getMin() == null) {
			// (-∞, = at least half-open towards min
			if (interval.getMax() == null) { // (-∞,∞) = infinite -> empty
				return VersionRange.NONE;
			} else {
				// (-∞,x = left open towards min -> half open towards max
				return new VersionRangeImpl(new VersionIntervalImpl(interval.getMax(), !interval.isMaxInclusive(), null, false));
			}
		} else if (interval.getMax() == null) {
			// x,∞) = half open towards max -> half open towards min
			return new VersionRangeImpl(new VersionIntervalImpl(null, false, interval.getMin(), !interval.isMinInclusive()));
		} else if (interval.getMin().equals(interval.getMax()) && !interval.isMinInclusive() && !interval.isMaxInclusive()) {
			// (x,x) = effectively empty interval -> infinite
			return VersionRange.ANY;
		} else {
			// closed interval -> 2 half open intervals on each side
			List<VersionInterval> ret = new ArrayList<>(2);
			ret.add(new VersionIntervalImpl(null, false, interval.getMin(), !interval.isMinInclusive()));
			ret.add(new VersionIntervalImpl(interval.getMax(), !interval.isMaxInclusive(), null, false));

			return new VersionRangeImpl(ret);
		}
	}

	public static VersionRange not(VersionRange intervals) {
		if (intervals.isEmpty()) return VersionRange.ANY;
		if (intervals.size() == 1) return not(intervals.iterator().next());


		// !(i0 || i1 || i2) is the same as !i0 && !i1 && !i2
		// In other words, a negation of a set of OR intervals is the same as negating each individual interval and
		// ANDing them together.
		VersionRange ret = null;

		for (VersionInterval v : intervals) {
			// negate the individual interval
			VersionRange inverted = not(v);

			// If there's no previous interval, just use the inverted one
			if (ret.isEmpty()) {
				ret = inverted;
			} else {
				// Use the boolean rule from earlier to build the result
				ret = and(ret, inverted);
			}

			if (ret.isEmpty()) break;
		}

		return ret;
	}
}
