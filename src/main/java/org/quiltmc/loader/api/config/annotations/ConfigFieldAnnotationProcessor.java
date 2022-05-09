/*
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

package org.quiltmc.loader.api.config.annotations;

import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors;

import java.lang.annotation.Annotation;

/**
 * Converts data in an annotation on a field to metadata on a {@link TrackedValue}.
 *
 * <p>See {@link Comment}
 */
public interface ConfigFieldAnnotationProcessor<T extends Annotation> {
	void process(T annotation, MetadataContainerBuilder<?> builder);

	static <T extends Annotation> void register(Class<T> annotationClass, ConfigFieldAnnotationProcessor<T> processor) {
		ConfigFieldAnnotationProcessors.register(annotationClass, processor);
	}
}
