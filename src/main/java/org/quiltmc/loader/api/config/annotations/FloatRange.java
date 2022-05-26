package org.quiltmc.loader.api.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.exceptions.ConfigFieldException;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FloatRange {
	double min();
	double max();

	final class Processor implements ConfigFieldAnnotationProcessor<FloatRange> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(FloatRange range, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				Object defaultValue = ((TrackedValue.Builder<?>) builder).getDefaultValue();

				if (defaultValue instanceof Float || defaultValue instanceof Double) {
					process(range, (TrackedValue.Builder<? extends Number>) builder);
				} else if (defaultValue instanceof CompoundConfigValue && (Float.class.isAssignableFrom(((CompoundConfigValue<?>) defaultValue).getType()) || Double.class.isAssignableFrom(((CompoundConfigValue<?>) defaultValue).getType()))) {
					((TrackedValue.Builder<CompoundConfigValue<Number>>) builder).constraint(Constraint.all(Constraint.range(range.min(), range.max())));
				} else {
					throw new ConfigFieldException("Constraint FloatRange not applicable for type '" + defaultValue.getClass() + "'");
				}
			}
		}

		private <T extends Number> void process(FloatRange range, TrackedValue.Builder<T> builder) {
			builder.constraint(Constraint.range(range.min(), range.max()));
		}
	}
}
