package org.quiltmc.loader.api;

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
	 * @return all mod dependencies this mod depends on
	 */
	Collection<ModDependency> depends();

	/**
	 * @return all mod dependencies this mod conflicts with
	 */
	Collection<ModDependency> breaks();

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
	String getIcon(int size);

	/**
	 * Checks for values from a mod's metadata.
	 *
	 * @return true if a value if the value exists, or falsew
	 */
	boolean containsRootValue(String key);

	/**
	 * Gets a values from a mod's metadata.
	 *
	 * <p>This can be used to access custom elements that are present in a {@code quilt.mod.json}.
	 *
	 * @return a value from a mod's metadata or null if the value does not exist
	 */
	@Nullable
	LoaderValue getValue(String key);

	/**
	 * Gets all values from a mod's metadata.
	 *
	 * <p>This can be used to access custom elements that are present in a {@code quilt.mod.json}.
	 *
	 * @return all the values
	 */
	Map<String, LoaderValue> values();

	/**
	 * Gets the value associated with a class in this mod metadata.
	 * This is typically used to provide game specific api.
	 *
	 * <p>At the moment these values can only be added by the game provider.
	 * In the future loader plugins will be able to offer additional values.
	 *
	 * @param type the class representing the type of object
	 * @param <T> the type
	 * @return the value
	 * @throws IllegalArgumentException if the type of value is not supported
	 */
	<T> T get(Class<T> type) throws IllegalArgumentException;
}
