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

package org.quiltmc.loader.impl.transformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.quiltmc.loader.api.Requires;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;

/** Contains string processing for both {@link PackageStrippingData} and {@link ClassStrippingData} */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public abstract class AbstractStripData extends ClassVisitor {

	protected final EnvType envType;
	protected final Set<String> mods;

	protected final List<String> denyLoadReasons = new ArrayList<>();

	protected AbstractStripData(int api, EnvType envType, Set<String> mods) {
		this(api, null, envType, mods);
	}

	protected AbstractStripData(int api, ClassVisitor classVisitor, EnvType envType, Set<String> mods) {
		super(api, classVisitor);
		this.envType = envType;
		this.mods = mods;
	}

	/** @return What this represents - generally "package" or "class". */
	protected abstract String type();

	public List<String> getDenyLoadReasons() {
		return Collections.unmodifiableList(denyLoadReasons);
	}

	public String summarizeDenyLoadReasons() {
		if (denyLoadReasons.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if (denyLoadReasons.size() == 1) {
			sb.append("because ");
			sb.append(denyLoadReasons.get(0));
		} else {
			sb.append("because:");
			for (String reason : denyLoadReasons) {
				sb.append("\n- ");
				sb.append(reason);
			}
		}
		return sb.toString();
	}

	/** Appends a client-only deny reason to {@link #denyLoadReasons}. */
	protected void denyClientOnlyLoad() {
		denyLoadReasons.add("the " + type() + " is annotated with @ClientOnly but we're on the dedicated server");
	}

	/** Appends a dedicated server only deny reason to {@link #denyLoadReasons}. */
	protected void denyDediServerOnlyLoad() {
		denyLoadReasons.add("the " + type() + " is annotated with @DedicatedServerOnly but we're on the client");
	}

	/** Checks to see if all mods given are in {@link #mods}, and appends a deny reason to {@link #denyLoadReasons} if
	 * any are missing. Assumes the annotation is {@link Requires} in the error message */
	protected void checkHasAllMods(List<String> requiredMods) {
		List<String> missingMods = new ArrayList<>();
		for (String mod : requiredMods) {
			if (!mods.contains(mod)) {
				missingMods.add(mod);
			}
		}

		checkHasAllMods(requiredMods, missingMods);
	}

	/** Checks to see if the missing mods list is empty, and appends a deny reason to {@link #denyLoadReasons} if it is
	 * not. Assumes the annotation is {@link Requires} in the error message */
	protected void checkHasAllMods(List<String> requiredMods, List<String> missingMods) {
		if (!missingMods.isEmpty()) {
			StringBuilder all = new StringBuilder();
			if (requiredMods.size() > 1) {
				all.append("{");
			}

			for (String mod : requiredMods) {
				if (all.length() > 1) {
					all.append(", ");
				}
				all.append("\"");
				all.append(mod);
				all.append("\"");
			}

			if (requiredMods.size() > 1) {
				all.append("}");
			}

			denyLoadReasons.add(
				"the " + type() + " is annotated with @Requires(" + all + ") but the mods " + missingMods
					+ " are not loaded!"
			);
		}
	}
}
