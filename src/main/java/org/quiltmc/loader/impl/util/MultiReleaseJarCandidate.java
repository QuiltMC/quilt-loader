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

/** Indicates to future quilt maintainers that the class is intended to be converted to a Multi-Release jar after we add
 * support for it, and then this annotation should be deleted.
 * <p>
 * (This merely allows for this to be searched) */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
@Retention(SOURCE)
@Target(TYPE)
public @interface MultiReleaseJarCandidate {

}
