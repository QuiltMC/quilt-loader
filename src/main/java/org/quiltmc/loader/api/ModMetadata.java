/*
 * Copyright 2016 FabricMC
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

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.FabricModMetadataWrapper;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.QuiltModMetadataWrapper;

/**
 * Representation of a mod's metadata.
 */
@ApiStatus.NonExtendable
public interface ModMetadata {
	// Required fields

	/**
	 * @return the id of this mod
	 */
	String id();

	/**
	 * @return the group of this mod
	 */
	String group();

	/**
	 * @return the version of this mod
	 */
	Version version();

	// Optional fields

	/**
	 * @return gets the human readable name for this mod
	 */
	String name();

	/**
	 * @return a description of this mod
	 */
	String description();

	/**
	 * Gets all licenses that apply to this mod.
	 *
	 * <p>The presence of a license does not imply whether multiple licenses all apply or may be chosen between,
	 * consult this mod's licensing for all the details.
	 *
	 * @return the licenses
	 */
	Collection<ModLicense> licenses();

	/**
	 * @return all this mod's contributors
	 */
	Collection<ModContributor> contributors();

	/**
	 * Gets an entry in this mod's contact information.
	 *
	 * @param key the key of the contact information entry
	 * @return the value of the contact information or null
	 */
	@Nullable
	String getContactInfo(String key);

	/**
	 * @return all contact information entries for this mod
	 */
	Map<String, String> contactInfo();

	/**
	 * @return all mod dependencies this mod has
	 */
	Collection<ModDependency> relations();

	/**
	 * Gets the path to an icon.
	 *
	 * <p>The standard defines icons as square .PNG files, however their
	 * dimensions are not defined - in particular, they are not
	 * guaranteed to be a power of two.</p>
	 *
	 * <p>The preferred size is used in the following manner:
	 * <ul><li>the smallest image larger than or equal to the size
	 * is returned, if one is present;</li>
	 * <li>failing that, the largest image is returned.</li></ul></p>
	 *
	 * @param size the preferred size
	 * @return the icon path, null if no applicable icon was found
	 */
	@Nullable
	String icon(int size);

	/**
	 * Checks for values from a mod's metadata. See {@link #values()} for the specific limitations of this API.
	 *
	 * @return true if a value if the value exists, or false otherwise.
	 */
	boolean containsValue(String key);

	/**
	 * Gets a value from a mod's metadata. See {@link #values()} for the specific limitations of this API.
	 * @return a value from a mod's metadata or null if the value does not exist
	 */
	@Nullable
	LoaderValue value(String key);

	/**
	 * Gets all available values in a mod's metadata.
	 *
	 * <p>This can be used to access a place in a mod's metadata where custom values may be defined.
	 * Depending on the format of the underlying implementation, this might include all of the data in the
	 * original metadata file (such as a {@code quilt.mod.json}), or only custom values, or nothing at all.
	 *
	 * @return all the values
	 */
	Map<String, LoaderValue> values();
}
