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
	 * @return the url to view the text of the license
	 */
	String url();

	/**
	 * @return a short description of the license, empty if there is no description
	 */
	String description();
}
