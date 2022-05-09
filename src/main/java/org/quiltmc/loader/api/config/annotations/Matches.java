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
public @interface Matches {
	String value();

	final class Processor implements ConfigFieldAnnotationProcessor<Matches> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(Matches matches, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				((TrackedValue.Builder<String>) builder).constraint(Constraint.matching(matches.value()));
			}
		}
	}
}
