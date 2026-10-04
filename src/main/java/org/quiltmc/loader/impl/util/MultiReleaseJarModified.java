/*
 * Copyright 2024 QuiltMC
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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Indicates that the annotated class has additional implementations in the various javaXX subprojects, and so changes
 * to the annotated class (such as new methods or fields) must be propagated to those additional subprojects. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
@Retention(SOURCE)
@Target(TYPE)
public @interface MultiReleaseJarModified {

}
