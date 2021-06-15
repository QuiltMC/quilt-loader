package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.metadata.qmj.ModDependencyIdentifierImpl;

@ApiStatus.NonExtendable
// TODO: document and make inner class
public interface ModDependencyIdentifier {
	/**
	 *
	 * @return the maven group, or an empty string for any
	 */
	String mavenGroup();
	String id();

	/**
	 * @return a string in the form <code>{@link #mavenGroup()}:{@link #id()}</code>, or simply {@link #id()}
	 * if <code>mavenGroup</code> is an empty string.
	 */
	String toString();
}
