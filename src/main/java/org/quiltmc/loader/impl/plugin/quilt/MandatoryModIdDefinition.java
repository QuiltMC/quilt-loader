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

package org.quiltmc.loader.impl.plugin.quilt;

import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A concrete definition that mandates that the modid must be loaded by the given singular {@link ModCandidate}, and no
 * others. (The resolver pre-validates that we don't have duplicate mandatory mods, so this is always valid by the time
 * this is used). */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class MandatoryModIdDefinition extends ModIdDefinition {
	public final ModLoadOption option;

	public MandatoryModIdDefinition(ModLoadOption candidate) {
		this.option = candidate;
	}

	@Override
	public String getModId() {
		return option.id();
	}

	@Override
	ModLoadOption[] sources() {
		return new ModLoadOption[] { option };
	}

	@Override
	public void define(RuleDefiner definer) {
		definer.atLeastOneOf(option);
	}

	@Override
	public boolean onLoadOptionAdded(LoadOption option) {
		return false;
	}

	@Override
	public boolean onLoadOptionRemoved(LoadOption option) {
		return false;
	}

	@Override
	String getFriendlyName() {
		return option.metadata().name() + " (" + option.id() + ")";
	}

	@Override
	public String toString() {
		return "mandatory " + option.fullString();
	}

	@Override
	public void fallbackErrorDescription(StringBuilder errors) {
		errors.append("Mandatory mod ");
		errors.append(getFriendlyName());
		errors.append(" v");
		errors.append(option.metadata().version());
	}
}
