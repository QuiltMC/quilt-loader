package org.quiltmc.loader.api.config.annotations;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.exceptions.ConfigFieldException;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IntegerRange {
	long min();
	long max();

	final class Processor implements ConfigFieldAnnotationProcessor<IntegerRange> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(IntegerRange range, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				Object defaultValue = ((TrackedValue.Builder<?>) builder).getDefaultValue();

				if (defaultValue instanceof Integer || defaultValue instanceof Long) {
					process(range, (TrackedValue.Builder<? extends Number>) builder);
				} else if (defaultValue instanceof CompoundConfigValue && (Integer.class.isAssignableFrom(((CompoundConfigValue<?>) defaultValue).getType()) || Long.class.isAssignableFrom(((CompoundConfigValue<?>) defaultValue).getType()))) {
					((TrackedValue.Builder<CompoundConfigValue<Number>>) builder).constraint(Constraint.all(Constraint.range(range.min(), range.max())));
				} else {
					throw new ConfigFieldException("Constraint IntegerRange not applicable for type '" + defaultValue.getClass() + "'");
				}
			}
		}

		private <T extends Number> void process(IntegerRange range, TrackedValue.Builder<T> builder) {
			builder.constraint(Constraint.range(range.min(), range.max()));
		}
	}
}
