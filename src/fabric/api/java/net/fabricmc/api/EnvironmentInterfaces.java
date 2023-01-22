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

package net.fabricmc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;

/**
 * A container of multiple {@link EnvironmentInterface} annotations on a class, often defined implicitly.
 * 
 * @deprecated Please use one of quilt's annotations: either {@link ClientOnly} or {@link DedicatedServerOnly}.
 * Those annotations can be directly applied to the implemented interface.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Documented
@Deprecated
public @interface EnvironmentInterfaces {
	/**
	 * Returns the {@link EnvironmentInterface} annotations it holds.
	 */
	EnvironmentInterface[] value();
}
