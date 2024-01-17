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

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Base definition of something that can either be completely loaded or not loaded. (Usually this is just a mod jar
 * file, but in the future this might refer to something else that loader has control over). */
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public abstract class LoadOption {

	/** @return A description of this load option, to be shown in the error gui when this load option is involved in a solver error. */
	public abstract QuiltLoaderText describe();

	public final LoadOption negate() {
		if (this instanceof NegatedLoadOption) {
			return ((NegatedLoadOption) this).not;
		} else {
			return new NegatedLoadOption(this);
		}
	}

	public static boolean isNegated(LoadOption option) {
		return option instanceof NegatedLoadOption;
	}

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
		if (isNegated(this) && isNegated(other)) {
			return negate().equals(other.negate());
		}
		return false;
	}

	@Override
	public final int hashCode() {
		if (isNegated(this)) {
			return ~negate().hashCode();
		}
		return super.hashCode();
	}

	// Internals

	/** A {@link Comparator} for any {@link LoadOption}. This is guaranteed to be stable in a single run of a JVM, not
	 * otherwise. */
	public static final Comparator<LoadOption> COMPARATOR = (a, b) -> Long.compare(a.order, b.order);

	private static final AtomicLong orderAssignment = new AtomicLong();
	private final long order;

	public LoadOption() {
		order = orderAssignment.incrementAndGet();
	}

	/* package-private */ LoadOption(LoadOption negated) {
		this.order = -negated.order;
	}
}
