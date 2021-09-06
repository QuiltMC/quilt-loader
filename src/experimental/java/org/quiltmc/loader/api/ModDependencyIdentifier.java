package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.metadata.qmj.ModDependencyIdentifierImpl;

/**
 * A dependency identifier is a combination of a maven group and a mod id.
 *
 * <p>Generally just the mod id will suffice for most dependency declarations.
 *
 * <p>The maven group is particularly useful in cases where depending on a specific fork of a mod is desired, typically
 * where mod compatibility does not work without a fork. The fork of the mod would declare it's {@code quilt.mod.json}
 * exactly the same as the upstream mod, but declare a different maven group to allow distinguishing between the two
 * mods sharing the same id inside dependency declarations.
 */
@ApiStatus.NonExtendable
// TODO: document and make inner class
public interface ModDependencyIdentifier {
	/**
	 * @return the maven group, or an empty string where no group requirement is specified
	 */
	String mavenGroup();

	/**
	 * @return the mod id of the dependent mod
	 */
	String id();

	/**
	 * @return a string in the form <code>{@link #mavenGroup()}:{@link #id()}</code>, or simply {@link #id()}
	 * if <code>mavenGroup</code> is an empty string.
	 */
	@Override
	String toString();
}
