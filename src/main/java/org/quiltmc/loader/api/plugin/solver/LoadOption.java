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

/** Base definition of something that can either be completely loaded or not loaded. (Usually this is just a mod jar
 * file, but in the future this might refer to something else that loader has control over). */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public abstract class LoadOption {

	// Overridden equals and hashCode to prevent solving from having strange behaviour

	@Override
	public final boolean equals(Object obj) {
		if (super.equals(obj)) {
			return true;
		}
		if (!(obj instanceof LoadOption)) {
			return false;
		}
		LoadOption other = (LoadOption) obj;
		if (RuleContext.isNegated(this) && RuleContext.isNegated(other)) {
			return RuleContext.negate(this).equals(RuleContext.negate(other));
		}
		return false;
	}

	@Override
	public final int hashCode() {
		if (RuleContext.isNegated(this)) {
			return ~RuleContext.negate(this).hashCode();
		}
		return super.hashCode();
	}

	/** @return A description of this load option, to be shown in the error gui when this load option is involved in a solver error. */
	public abstract QuiltLoaderText describe();
}
