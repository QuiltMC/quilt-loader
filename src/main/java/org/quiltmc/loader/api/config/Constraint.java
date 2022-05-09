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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Some predicate that is used to validate {@link TrackedValue} values.
 *
 * <p>See {@link TrackedValue.Builder#constraint(Constraint)}
 */
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

	static <T extends Number> Constraint<T> range(long from, long to) {
		return new Range<>(from, to, Long::compareTo, Number::longValue);
	}

	static <T extends Number> Constraint<T> range(double from, double to) {
		return new Range<>(from, to, Double::compareTo, Number::doubleValue);
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
				return "matches " + regex;
			}
		};
	}

	class Range<T, BOUNDS> implements Constraint<T> {
		private final BOUNDS min, max;
		private final Comparator<BOUNDS> comparator;
		private final Function<T, BOUNDS> function;

		public Range(BOUNDS min, BOUNDS max, Comparator<BOUNDS> comparator, Function<T, BOUNDS> function) {
			this.min = min;
			this.max = max;
			this.comparator = comparator;
			this.function = function;
		}

		@Override
		public Optional<String> test(T value) {
			int minTest = this.comparator.compare(this.min, this.function.apply(value));
			int maxTest = this.comparator.compare(this.max, this.function.apply(value));

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
