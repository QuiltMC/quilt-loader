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

package org.quiltmc.loader.impl.solver;

/** Used for the "inverse load" condition - if this is required by a {@link Rule} then it means the
 * {@link LoadOption} must not be loaded. */
final class NegatedLoadOption extends LoadOption {
	final LoadOption not;

	public NegatedLoadOption(LoadOption not) {
		this.not = not;
	}

	@Override
	public String toString() {
		return "NOT " + not;
	}
}
