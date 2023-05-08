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
class VersionRangeDescriber {
	private static final String titleKey = "range";
	static QuiltLoaderText describe(VersionRange range) {

		if (range.size() != 1) {
			return QuiltLoaderText.translate(titleKey, range);
		}

		VersionInterval interval = range.first();

		// Negative infinity
		if (interval.getMin() == null) {
			// Positive infinity
			if (interval.getMax() == null) {
				return QuiltLoaderText.translate(titleKey + ".any");
			} else {
				return QuiltLoaderText.translate(titleKey + (interval.isMaxInclusive() ? ".lesser_equal" : ".lesser"), interval.getMax());
			}
		}

		// positive infinity
		if (interval.getMax() == null) {
			return QuiltLoaderText.translate(titleKey + (interval.isMinInclusive() ? ".greater_equal" : ".greater"), interval.getMin());
		}

		if (interval.getMax().equals(interval.getMin())) {
			return QuiltLoaderText.translate(titleKey + ".exact", interval.getMax());
		}

		// ranged
		String extra = (interval.isMinInclusive() ? "inc_" : "exc_") + (interval.isMaxInclusive() ? "inc" : "exc");

		return QuiltLoaderText.translate(titleKey + extra, interval.getMin(), interval.getMax());
	}
}
