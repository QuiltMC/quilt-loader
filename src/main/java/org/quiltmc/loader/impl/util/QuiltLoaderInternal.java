package org.quiltmc.loader.impl.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Indicates that the specified quilt-loader class is internal, and not loadable by mods.
 * <p>
 * Quilt-loader plugin APIs are annotated with this, but use {@link QuiltLoaderInternalType#PLUGIN_API} as their value -
 * which indicates that it's the only quilt-loader classes that loader plugins may use.
 * <p>
 * By default this marks the class as "legacy_exposed", which doesn't throw an exception when trying to access it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QuiltLoaderInternal {

	/** Controls how "internal" the class is. */
	QuiltLoaderInternalType value();
}
