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

package org.quiltmc.loader.impl.config.builders;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;

import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.impl.config.util.ConfigFieldAnnotationProcessors;
import org.quiltmc.loader.impl.config.util.ConfigUtils;

public class ReflectiveConfigCreator<C> implements Config.Creator {
	private final Class<C> creatorClass;
	private C instance;

	public ReflectiveConfigCreator(Class<C> creatorClass) {
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
				TrackedValue<?> value = TrackedValue.create(defaultValue, key.getFirst(), valueBuilder -> {
					boolean add = false;

					for (String k : key) {
						if (add) {
							valueBuilder.key(k);
						}

						add = true;
					}

					field.setAccessible(true);

					valueBuilder.callback(tracked -> {
						try {
							field.set(object, tracked.getValue());
						} catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					});

					for (Annotation annotation : field.getAnnotations()) {
						ConfigFieldAnnotationProcessors.applyAnnotationProcessors(annotation, valueBuilder);
					}
				});

				field.set(object, value.getRealValue());
				builder.field(value);
			} else if (defaultValue != null) {
				// TODO: Add support for section comments on subclasses

				for (Field f : defaultValue.getClass().getDeclaredFields()) {
					if (!f.isSynthetic()) {
						this.createField(builder, key, defaultValue, f);
					}
				}
			} else {
				throw new RuntimeException("Config value cannot be null");
			}

			key.removeLast();
		}

	}

	public void create(Config.Builder builder) {
		if (this.instance != null) {
			throw new RuntimeException();
		}

		try {
			this.instance = creatorClass.newInstance();

			Deque<String> key = new ArrayDeque<>();

			for (Field field : this.creatorClass.getDeclaredFields()) {
				this.createField(builder, key, this.instance, field);
			}
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static <C> ReflectiveConfigCreator<C> of(Class<C> creatorClass) {
		return new ReflectiveConfigCreator<>(creatorClass);
	}

	public C getInstance() {
		if (this.instance == null) {
			throw new RuntimeException();
		}

		return this.instance;
	}
}
