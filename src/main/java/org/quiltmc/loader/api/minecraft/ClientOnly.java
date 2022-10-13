/*
 * Copyright 2016 FabricMC
 * Copyright 2022 QuiltMC
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

package org.quiltmc.loader.api.minecraft;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applied to declare that the annotated element is present only in the client environment.
 * <p>
 * When applied to mod code this will result in quilt-loader removing that element when running on the dedicated server.
 * <p>
 * When the annotated element is removed, bytecode associated with the element will not be removed. For example, if a
 * field is removed, its initializer code will not, and will cause an error on execution.
 * <p>
 * If an overriding method has this annotation and its overridden method doesn't, unexpected behavior may happen. If an
 * overridden method has this annotation while the overriding method doesn't, it is safe, but the method can be used
 * from the overridden class only in the specified environment.
 * <p>
 * When applied to an implemented interface (via {@link ElementType#TYPE_USE}) it will be removed on the dedicated server.
 * Overridden interface methods need to be annotated separately in order to be removed.
 *
 * @see DedicatedServerOnly
 * @see ClientOnlyInterface */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE })
@Documented
public @interface ClientOnly {

	/** @return True if lambda methods referenced by this method should also be stripped. Has no effect when used to
	 *         annotate classes, implements declarations, or fields. */
	boolean stripLambdas() default true;
}
