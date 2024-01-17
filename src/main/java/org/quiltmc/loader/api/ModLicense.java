/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.qmj.ModLicenseImpl;

/**
 * Representation of a license a mod may use.
 * 
 * @see <a href="https://spdx.org/licenses/">SPDX identifier</a>
 */
@ApiStatus.NonExtendable
public interface ModLicense {
	/**
	 * @return the name of the license
	 */
	String name();

	/**
	 * @return the short identifier of the license, typically an
	 * <a href="https://spdx.org/licenses/">SPDX identifier</a>
	 */
	String id();

	/**
	 * @return the url to view the text of the license. May be empty.
	 */
	String url();

	/**
	 * @return a short description of the license, empty if there is no description
	 */
	String description();

	/** Looks up a {@link ModLicense} from the given SPDX license ID, returning null if quilt-loader is unaware of
	 * it. */
	@Nullable
	public static ModLicense fromIdentifier(String identifier) {
		return ModLicenseImpl.fromIdentifier(identifier);
	}

	/** Looks up a {@link ModLicense} from the given SPDX license ID, returning a new {@link ModLicense} (with
	 * {@link #name()} and {@link #id()} set to the passed identifier, the other fields blank) if quilt-loader is
	 * unaware of it. */
	public static ModLicense fromIdentifierOrDefault(String identifier) {
		return ModLicenseImpl.fromIdentifierOrDefault(identifier);
	}
}
