/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.api.plugin.solver;

import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Used for the "inverse load" condition - if this is required by a {@link Rule} then it means the {@link LoadOption}
 * must not be loaded.
 * <p>
 * Plugins can negate {@link LoadOption}s with {@link LoadOption#negate()}, and test for negation with either
 * "instanceof NegatedLoadOption" or LoadOption.isNegated */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public final class NegatedLoadOption extends LoadOption {
	public final LoadOption not;

	/* package-private */ NegatedLoadOption(LoadOption not) {
		super(not);
		if (not instanceof NegatedLoadOption) {
			throw new IllegalArgumentException("Found double-negated negated load option!");
		}
		this.not = not;
	}

	@Override
	public String toString() {
		return "NOT " + not;
	}

	@Override
	public QuiltLoaderText describe() {
		return QuiltLoaderText.translate("solver.option.negated", not.describe());
	}
}
