/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Representation of a license a mod may use.
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
}
