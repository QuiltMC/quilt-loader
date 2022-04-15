/*
 * Copyright 2016 FabricMC
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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to declare that a class implements an interface only on the client.
 *
 * <p>Use with caution, as Quilt-loader will remove the interface from {@code implements} declaration
 * of the class on the dedicated server.
 *
 * <p>Implemented methods are not removed. To remove implemented methods, use {@link ClientOnly}.</p>
 *
 * @see ClientOnly
 * @see DedicatedServerOnlyInterface
 */
@Retention(RetentionPolicy.CLASS)
@Repeatable(ClientOnlyInterfaces.class)
@Target(ElementType.TYPE)
@Documented
public @interface ClientOnlyInterface {

	/**
	 * Returns the interface class.
	 */
	Class<?> value();
}
