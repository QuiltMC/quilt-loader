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

package org.quiltmc.loader.impl.util;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public enum QuiltLoaderInternalType {

	/** Indicates the class is both legacy, and was originally considered "api" - so no warnings should be printed on
	 * callers. */
	LEGACY_NO_WARN,

	/** Indicates the class is legacy, and not considered "api" at any point - so warnings should be printed when
	 * callers try to access it. */
	LEGACY_EXPOSED,

	/** Permits loader plugins to access the class, but not mods. */
	PLUGIN_API,

	/** Indicates the class has been added since 0.18.0, and so both mods and plugins aren't allowed to use them. */
	NEW_INTERNAL;
}
