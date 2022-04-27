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

package org.quiltmc.loader.impl.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;

import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.TrackedValue;

public class ReflectiveConfigCreatorImpl<C> {
	private final Class<C> creatorClass;

	public ReflectiveConfigCreatorImpl(Class<C> creatorClass) {
		this.creatorClass = creatorClass;
	}

	private void createField(Config.Builder builder, Deque<String> key, Object object, Field field) throws IllegalAccessException {
		if (!Modifier.isFinal(field.getModifiers())) {
			throw new RuntimeException("Field '" + field.getType().getName() + ':' + field.getName() + "' is not final");
		}

		if (!Modifier.isStatic(field.getModifiers())) {
			key.add(field.getName());

			Object defaultValue = field.get(object);

			if (ConfigUtils.isValidValue(defaultValue)) {
				builder.field(TrackedValue.create(defaultValue, key.getFirst(), valueBuilder -> {
					boolean add = false;

					for (String k : key) {
						if (add) {
							valueBuilder.key(k);
						}

						add = true;
					}

					valueBuilder.callback((k, oldValue, newValue) -> {
						try {
							field.setAccessible(true);
							field.set(object, newValue);
							field.setAccessible(false);
						} catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					});

					for (Annotation annotation : field.getAnnotations()) {
						ConfigFieldAnnotationProcessors.applyAnnotationProcessors(annotation, valueBuilder);
					}
				}));
			} else {
				for (Field f : defaultValue.getClass().getDeclaredFields()) {
					if (!f.isSynthetic()) {
						this.createField(builder, key, defaultValue, f);
					}
				}
			}

			key.removeLast();
		}

	}

	public C create(Config.Builder builder) {
		try {
			C c = creatorClass.newInstance();

			Deque<String> key = new ArrayDeque<>();

			for (Field field : this.creatorClass.getDeclaredFields()) {
				this.createField(builder, key, c, field);
			}

			return c;
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static <C> ReflectiveConfigCreatorImpl<C> of(Class<C> creatorClass) {
		return new ReflectiveConfigCreatorImpl<>(creatorClass);
	}
}
