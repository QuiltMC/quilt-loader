/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.plugin;

import org.quiltmc.loader.api.VersionInterval;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import java.util.stream.Collectors;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class VersionRangeDescriber {
	/**
	 * Describes the relationship between {@code modFrom} to the specified dependency information.
	 *
	 * @param modFrom    the mod that has the relationship
	 * @param range      the version range
	 * @param depName    the name of the dependency
	 * @param isDep      {@code true} if the dependency is required dependency, and {@code false} is it is a breaking dependency
	 * @param transitive {@code true} if the relationship is transitive
	 * @return the translation for the dependency relationship
	 *
	 * @see VersionRangeDescriber#describe(QuiltLoaderText, VersionRange, String, boolean, boolean)
	 * @see VersionRangeDescriber#describe(VersionRange, String)
	 */
	public static QuiltLoaderText describe(String modFrom, VersionRange range, String depName, boolean isDep, boolean transitive) {
		return describe(QuiltLoaderText.of(modFrom), range, depName, isDep, transitive);
	}

	/**
	 * Describes the relationship between {@code modFrom} to the specified dependency information.
	 *
	 * @param modFrom    the mod that has the relationship
	 * @param range      the version range
	 * @param depName    the name of the dependency
	 * @param isDep      {@code true} if the dependency is required dependency, and {@code false} is it is a breaking dependency
	 * @param transitive {@code true} if the relationship is transitive
	 * @return the translation for the dependency relationship
	 *
	 * @see VersionRangeDescriber#describe(String, VersionRange, String, boolean, boolean)
	 * @see VersionRangeDescriber#describe(VersionRange, String)
	 */
	public static QuiltLoaderText describe(QuiltLoaderText modFrom, VersionRange range, String depName, boolean isDep, boolean transitive) {
		String titleKey = "error." + (isDep ? "dep." : "break.") + (transitive ? "transitive." : "direct.");

		if (range.size() != 1) {
			for (VersionInterval interval : range) {
				// Handle the specific case of { [A,A] U [B,B] } (of any length)
				if (interval.getMin() == null || !interval.getMin().equals(interval.getMax())) {
					return QuiltLoaderText.translate(getTransKey(titleKey, "ranged"), modFrom, range, depName);
				}
			}

			VersionInterval finalV = range.last();
			//noinspection DataFlowIssue -- we've confirmed that it can never be null above
			String list = range.headSet(finalV).stream().map(i -> i.getMin().toString()).collect(Collectors.joining(", "));
			return QuiltLoaderText.translate(getTransKey(titleKey, "exact_list"), modFrom, list, finalV.getMin(), depName);
		}

		VersionInterval interval = range.first();

		// Negative infinity
		if (interval.getMin() == null) {
			// Positive infinity
			if (interval.getMax() == null) {
				return QuiltLoaderText.translate(getTransKey(titleKey, "any"), modFrom, depName);
			} else {
				return QuiltLoaderText.translate(getTransKey(titleKey, interval.isMaxInclusive() ? "lesser_equal" : "lesser"), modFrom, interval.getMax(), depName);
			}
		}

		// positive infinity
		if (interval.getMax() == null) {
			return QuiltLoaderText.translate(getTransKey(titleKey, interval.isMinInclusive() ? "greater_equal" : "greater"), modFrom, interval.getMin(), depName);
		}

		if (interval.getMax().equals(interval.getMin())) {
			return QuiltLoaderText.translate(getTransKey(titleKey, "exact"), modFrom, interval.getMax(), depName);
		}

		// ranged
		String extra = "range_" + (interval.isMinInclusive() ? "inc_" : "exc_") + (interval.isMaxInclusive() ? "inc" : "exc");

		return QuiltLoaderText.translate(getTransKey(titleKey, extra), modFrom, interval.getMin(), interval.getMax(), depName);
	}

	/**
	 * Describes just the version range for a dependency.
	 *
	 * @param range   the range
	 * @param depName the dependency name
	 * @return the description text
	 *
	 * @see VersionRangeDescriber#describe(String, VersionRange, String, boolean, boolean)
	 * @see VersionRangeDescriber#describe(QuiltLoaderText, VersionRange, String, boolean, boolean)
	 */
	public static QuiltLoaderText describe(VersionRange range, String depName) {
		String titleKey = "error.";

		if (range.size() != 1) {
			for (VersionInterval interval : range) {
				// Handle the specific case of { [A,A] U [B,B] } (of any length)
				if (interval.getMin() == null || !interval.getMin().equals(interval.getMax())) {
					return QuiltLoaderText.translate(getTransKey(titleKey, "ranged"), range, depName);
				}
			}

			VersionInterval finalV = range.last();
			//noinspection DataFlowIssue -- we've confirmed that it can never be null above
			String list = range.headSet(finalV).stream().map(i -> i.getMin().toString()).collect(Collectors.joining(", "));
			return QuiltLoaderText.translate(getTransKey(titleKey, "exact_list"), list, finalV.getMin(), depName);
		}

		VersionInterval interval = range.first();

		// Negative infinity
		if (interval.getMin() == null) {
			// Positive infinity
			if (interval.getMax() == null) {
				return QuiltLoaderText.translate(getTransKey(titleKey, "any"), depName);
			} else {
				return QuiltLoaderText.translate(getTransKey(titleKey, interval.isMaxInclusive() ? "lesser_equal" : "lesser"), interval.getMax(), depName);
			}
		}

		// positive infinity
		if (interval.getMax() == null) {
			return QuiltLoaderText.translate(getTransKey(titleKey, interval.isMinInclusive() ? "greater_equal" : "greater"), interval.getMin(), depName);
		}

		if (interval.getMax().equals(interval.getMin())) {
			return QuiltLoaderText.translate(getTransKey(titleKey, "exact"), interval.getMax(), depName);
		}

		// ranged
		String extra = "range_" + (interval.isMinInclusive() ? "inc_" : "exc_") + (interval.isMaxInclusive() ? "inc" : "exc");

		return QuiltLoaderText.translate(getTransKey(titleKey, extra), interval.getMin(), interval.getMax(), depName);
	}

	private static String getTransKey(String titleKey, String extra) {
		return titleKey + extra + ".title";
	}
}
