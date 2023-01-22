/*
 * Copyright 2022, 2023 QuiltMC
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
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public @interface QuiltLoaderInternal {

	/** Controls how "internal" the class is. */
	QuiltLoaderInternalType value();

	/** Indicates API classes which should be used instead of the annotated class. */
	Class<?>[] replacements() default {};
}
