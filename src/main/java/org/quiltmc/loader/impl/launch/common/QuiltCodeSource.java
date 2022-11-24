package org.quiltmc.loader.impl.launch.common;

import java.security.CodeSource;
import java.util.Optional;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;

/** For {@link CodeSource}s. */
public interface QuiltCodeSource {
	/** @return The mod that contains this class. (This is used to implement
	 *         {@link QuiltLoader#getModContainer(Class)}). */
	Optional<ModContainer> getQuiltMod();
}
