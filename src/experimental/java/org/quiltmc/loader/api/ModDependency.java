package org.quiltmc.loader.api;

import java.util.Collection;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

/**
 * Representation of a mod's dependency.
 */
@ApiStatus.NonExtendable
public interface ModDependency {
	/**
	 * @return the mod identifier that the dependency tries to check for
	 */
	String id();

	/**
	 * @return version constraints that this dependency
	 */
	Set<VersionConstraint> versions();

	/**
	 * @return a reason to describe why this dependency exists
	 */
	String reason();

	/**
	 * Gets all dependencies that must be satisfied to allow this dependency to be active.
	 *
	 * @return a the mod dependencies
	 */
	Collection<ModDependency> unless();

	/**
	 * Checks if the mod dependency is currently active and is used in dependency resolution.
	 * This correlates to the {@code optional} field in a dependency object.
	 *
	 * @return if this mod dependency is active and used.
	 */
	boolean active();

	/**
	 * Checks if this dependency matches a specific version.
	 *
	 * <p>This will only return true if
	 * <ul>
	 * <li> the dependency is active
	 * <li> no dependency exceptions matched in {@link #unless()}
	 * <li> any version constraint is matched
	 * </ul>
	 *
	 * @param version the version to check
	 * @return true if the version matches or else false
	 */
	boolean matches(Version version);
}
