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

	private void createField(Config.SectionBuilder builder, Object object, Field field) throws IllegalAccessException {
		if (!Modifier.isFinal(field.getModifiers())) {
			throw new RuntimeException("Field '" + field.getType().getName() + ':' + field.getName() + "' is not final");
		}

		if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
			Object defaultValue = field.get(object);

			if (ConfigUtils.isValidValue(defaultValue)) {
				TrackedValue<?> value = TrackedValue.create(defaultValue, field.getName(), valueBuilder -> {
					field.setAccessible(true);

					valueBuilder.callback(tracked -> {
						try {
							field.set(object, tracked.value());
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
			} else if (defaultValue instanceof Config.Section) {
				builder.section(field.getName(), b -> {
					for (Annotation annotation : field.getAnnotations()) {
						ConfigFieldAnnotationProcessors.applyAnnotationProcessors(annotation, b);
					}

					for (Field f : defaultValue.getClass().getDeclaredFields()) {
						if (!f.isSynthetic()) {
							try {
								this.createField(b, defaultValue, f);
							} catch (IllegalAccessException e) {
								throw new RuntimeException(e);
							}
						}
					}
				});
			} else if (defaultValue == null) {
				throw new RuntimeException("Default value for field '" + field.getName() + "' cannot be null");
			} else {
				throw new RuntimeException("Class '" + defaultValue.getClass().getName() + "' of field '" + field.getName() + "' is not a valid config value; must be a basic type, complex type, or implement org.quiltmc.loader.api.config.Config.Section");
			}
		}
	}

	public void create(Config.Builder builder) {
		if (this.instance != null) {
			throw new RuntimeException();
		}

		try {
			this.instance = creatorClass.newInstance();

			for (Field field : this.creatorClass.getDeclaredFields()) {
				this.createField(builder, this.instance, field);
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
