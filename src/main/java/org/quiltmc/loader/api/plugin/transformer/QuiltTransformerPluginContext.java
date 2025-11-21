/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.loader.api.plugin.transformer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public interface QuiltTransformerPluginContext {
	/**
	 * Register external inputs that would require the transform cache to be regenerated.
	 * The transform cache is always regenerated when mod files change;
	 * this is for keeping track of information like config files or system properties.
	*/
	void registerInput(String key, String value);

	TransformBuilder createTransformation(String id);

	interface TransformBuilder {
		TransformBuilder before(String id);
		TransformBuilder after(String id);
		void register(TransformCacheConsumer consumer);
	}

	@FunctionalInterface
	interface TransformCacheConsumer extends Consumer<TransformCache> {
		void acceptThrowing(TransformCache cache) throws IOException;
		@Override
		default void accept(TransformCache cache) {
			try {
				this.acceptThrowing(cache);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
