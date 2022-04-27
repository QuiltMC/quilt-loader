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

package org.quiltmc.loader.api.config;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

public interface Constraint<T> {
	/**
	 * @param value the value to test against this {@link Constraint}
	 * @return an optional that contains an error message if the value does not pass, and that is empty if it does
	 */
	Optional<String> test(T value);

	/**
	 * @return a clean and concise representation of this {@link Constraint}
	 */
	String getRepresentation();

	static Range<Integer> range(int from, int to) {
		return new Range<>(from, to, Integer::compareTo);
	}

	static Range<Long> range(long from, long to) {
		return new Range<>(from, to, Long::compareTo);
	}

	static Range<Float> range(float from, float to) {
		return new Range<>(from, to, Float::compareTo);
	}

	static Range<Double> range(double from, double to) {
		return new Range<>(from, to, Double::compareTo);
	}

	static Constraint<String> matching(String regex) {
		return new Constraint<String>() {
			private final Pattern pattern = Pattern.compile(regex);

			@Override
			public Optional<String> test(String value) {
				if (pattern.matcher(value).matches()) {
					return Optional.empty();
				} else {
					return Optional.of(String.format("Value '%s' does not match pattern '%s'", value, regex));
				}
			}

			@Override
			public String getRepresentation() {
				return "matches" + regex;
			}
		};
	}

	class Range<T extends Number> implements Constraint<T> {
		private final T min, max;
		private final Comparator<T> comparator;

		public Range(T min, T max, Comparator<T> comparator) {
			this.min = min;
			this.max = max;
			this.comparator = comparator;
		}

		@Override
		public Optional<String> test(T value) {
			int minTest = this.comparator.compare(this.min, value);
			int maxTest = this.comparator.compare(this.max, value);

			if (minTest <= 0 && maxTest >= 0) {
				return Optional.empty();
			} else {
				return Optional.of(String.format("Value '%s' outside of range [%s, %s]", value, this.min, this.max));
			}
		}

		@Override
		public String getRepresentation() {
			return "range[" + this.min + ", " + this.max + "]";
		}
	}
}
