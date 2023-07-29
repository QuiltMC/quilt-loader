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

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class VersionRangeDescriber {
	public static QuiltLoaderText describe(String modName, VersionRange range, String depName, boolean transitive) {
		return describe(QuiltLoaderText.of(modName), range, depName, transitive);
	}

	public static QuiltLoaderText describe(QuiltLoaderText modFrom, VersionRange range, String depName, boolean transitive) {
		return describe(modFrom, range, depName, true, transitive);
	}

	public static QuiltLoaderText describe(String modFrom, VersionRange range, String depName, boolean isDep, boolean transitive) {
		return describe(QuiltLoaderText.of(modFrom), range, depName, isDep, transitive);
	}

	public static QuiltLoaderText describe(QuiltLoaderText modFrom, VersionRange range, String depName, boolean isDep, boolean transitive) {
		String titleKey = "error." + (isDep ? "dep." : "break.") + (transitive ? "transitive." : "direct.");

		if (range.size() != 1) {
			return QuiltLoaderText.translate(getTransKey(titleKey, "ranged"), modFrom, range, depName);
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

	private static String getTransKey(String titleKey, String extra) {
		return titleKey + extra + ".title";
	}
}
