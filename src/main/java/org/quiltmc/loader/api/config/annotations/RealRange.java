package org.quiltmc.loader.api.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RealRange {
	double min();
	double max();

	final class Processor implements ConfigFieldAnnotationProcessor<RealRange> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(RealRange range, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				process(range, (TrackedValue.Builder<? extends Number>) builder);
			}
		}

		private <T extends Number> void process(RealRange range, TrackedValue.Builder<T> builder) {
			builder.constraint(Constraint.range(range.min(), range.max()));
		}
	}
}
