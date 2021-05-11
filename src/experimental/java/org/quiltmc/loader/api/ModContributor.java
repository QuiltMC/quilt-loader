package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * A contributor to a mod.
 */
@ApiStatus.NonExtendable
public interface ModContributor {
	/**
	 * @return the name of the contributor
	 */
	String name();

	/**
	 * @return the role that represents a contributor's relation to a mod.
	 */
	String role();
}
