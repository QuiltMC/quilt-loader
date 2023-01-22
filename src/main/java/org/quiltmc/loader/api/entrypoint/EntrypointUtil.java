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

package org.quiltmc.loader.api.entrypoint;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.entrypoint.EntrypointUtils;

/** Various methods for invoking entrypoints. This is intended for use whenever you want to invoke a method on each
 * returned entrypoint, likely via method reference. For example Quilt Loader uses this to invoke the "pre_launch"
 * entrypoint:<br>
 * <code>
 * EntrypointUtil.invoke("pre_launch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
 * </code> */
public class EntrypointUtil {

	/** Passes every entrypoint value with the given name to the consumer.
	 * 
	 * @throws EntrypointException if anything goes wrong while gathering entrypoints (for example if an entrypoint
	 *             cannot be cast to the given class). */
	public static <T> void invoke(String name, Class<T> type, Consumer<? super T> invoker) {
		invokeContainer(name, type, container -> invoker.accept(container.getEntrypoint()));
	}

	/** Passes every entrypoint value and {@link ModContainer} with the given name to the consumer.
	 * 
	 * @throws EntrypointException if anything goes wrong while gathering entrypoints (for example if an entrypoint
	 *             cannot be cast to the given class). */
	public static <T> void invoke(String name, Class<T> type, BiConsumer<T, ModContainer> invoker) {
		invokeContainer(name, type, container -> invoker.accept(container.getEntrypoint(), container.getProvider()));
	}

	/** Passes every {@link EntrypointContainer} with the given name to the consumer.
	 * 
	 * @throws EntrypointException if anything goes wrong while gathering entrypoints (for example if an entrypoint
	 *             cannot be cast to the given class). */
	public static <T> void invokeContainer(String name, Class<T> type, Consumer<EntrypointContainer<T>> invoker) {
		EntrypointUtils.invokeContainer(name, type, invoker);
	}
}
