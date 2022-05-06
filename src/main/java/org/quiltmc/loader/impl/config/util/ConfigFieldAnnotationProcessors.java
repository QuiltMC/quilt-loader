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

package org.quiltmc.loader.impl.config.util;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor;

public final class ConfigFieldAnnotationProcessors {
	private static final Map<Class<? extends Annotation>, List<ConfigFieldAnnotationProcessor<?>>> PROCESSORS = new HashMap<>();

	static {
		register(Comment.class, new Comment.Processor());
	}

	public static <T extends Annotation> void register(Class<T> annotationClass, ConfigFieldAnnotationProcessor<T> processor) {
		PROCESSORS.computeIfAbsent(annotationClass, c -> new ArrayList<>())
				.add(processor);
	}

	private static <T extends Annotation> void process(ConfigFieldAnnotationProcessor<T> processor, T annotation, MetadataContainerBuilder<?> builder) {
		processor.process(annotation, builder);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void applyAnnotationProcessors(Annotation annotation, MetadataContainerBuilder<?> builder) {
		for (ConfigFieldAnnotationProcessor<?> processor : PROCESSORS.getOrDefault(annotation.annotationType(), Collections.emptyList())) {
			process((ConfigFieldAnnotationProcessor) processor, annotation, builder);
		}
	}
}
