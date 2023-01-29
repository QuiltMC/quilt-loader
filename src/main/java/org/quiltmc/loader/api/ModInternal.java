/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Indicates that the annotated package, class, method, constructor, or field is not considered to be part of a mods
 * public api, and so quilt-loader should restrict access to only the mods specified in {@link #exceptions()}.
 * <p>
 * If both a class and a method are annotated with this then the more specific annotation is read.
 * <p>
 * This is generally intended for library mods with a clearly defined api, and probably only those which use semantic
 * versioning (or similar) to describe when their public API changes. Content mods which have an API too are generally
 * discouraged from using this.
 * <p>
 * Care must be taken when mixins defined by the mod access elements annotated with this, as the target mod must also be
 * included in the {@link #exceptions()} array. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD })
public @interface ModInternal {

	/** An array of mod ids, which are allowed to access the annotated element. This array is always treated as if it
	 * contains the owning mod id, so it doesn't need to be present. */
	String[] exceptions() default {};

	// TODO: Custom message?

	/** Indicates API classes which should be used instead of the annotated class. */
	Class<?>[] classReplacements() default {};

	/** Indicates replacements which should be used instead of the annotated element. */
	String[] replacements() default {};
}
