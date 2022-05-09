package org.quiltmc.loader.api.config.annotations;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DiscreteRange {
	long min();
	long max();

	final class Processor implements ConfigFieldAnnotationProcessor<DiscreteRange> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(DiscreteRange range, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				Object defaultValue = ((TrackedValue.Builder<?>) builder).getDefaultValue();

				if (defaultValue instanceof Number) {
					process(range, (TrackedValue.Builder<? extends Number>) builder);
				} else if (defaultValue instanceof CompoundConfigValue && Number.class.isAssignableFrom(((CompoundConfigValue<?>) defaultValue).getType())) {
					((TrackedValue.Builder<CompoundConfigValue<Number>>) builder).constraint(Constraint.all(Constraint.range(range.min(), range.max())));
				}
			}
		}

		private <T extends Number> void process(DiscreteRange range, TrackedValue.Builder<T> builder) {
			builder.constraint(Constraint.range(range.min(), range.max()));
		}
	}
}
