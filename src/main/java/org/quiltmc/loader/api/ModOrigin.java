package org.quiltmc.loader.api;


import java.nio.file.Path;
import java.util.List;

/**
 * Representation of the various locations a mod was loaded from originally.
 *
 * <p>This location is not necessarily identical to the code source used at runtime, a mod may get copied or otherwise
 * transformed before being put on the class path. It thus mostly represents the installation and initial loading, not
 * what is being directly accessed at runtime.
 */
public interface ModOrigin {
	/**
	 * Get the kind of this origin, determines the available methods.
	 *
	 * @return mod origin kind
	 */
	Kind getKind();

	/**
	 * Get the jar or folder paths for a {@link org.quiltmc.loader.api.ModOrigin.Kind#PATH} origin.
	 *
	 * @return jar or folder paths
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	List<Path> getPaths();

	/**
	 * Get the parent mod for a {@link org.quiltmc.loader.api.ModOrigin.Kind#NESTED} origin.
	 *
	 * @return parent mod
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	String getParentModId();

	/**
	 * Get the jar or folder paths for a {@link org.quiltmc.loader.api.ModOrigin.Kind#PATH} origin.
	 *
	 * @return jar or folder paths
	 * @throws UnsupportedOperationException for incompatible kinds
	 */
	String getParentSubLocation();

	/**
	 * Non-exhaustive list of possible {@link org.quiltmc.loader.api.ModOrigin} kinds.
	 *
	 * <p>New kinds may be added in the future, use a default switch case!
	 */
	enum Kind {
		PATH, NESTED, UNKNOWN
	}
}
