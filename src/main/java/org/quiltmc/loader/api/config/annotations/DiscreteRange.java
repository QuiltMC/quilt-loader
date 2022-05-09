package org.quiltmc.loader.api.config.annotations;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;

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
				process(range, (TrackedValue.Builder<? extends Number>) builder);
			}
		}

		private <T extends Number> void process(DiscreteRange range, TrackedValue.Builder<T> builder) {
			builder.constraint(Constraint.range(range.min(), range.max()));
		}
	}
}
