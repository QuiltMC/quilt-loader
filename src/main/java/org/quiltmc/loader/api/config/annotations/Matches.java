package org.quiltmc.loader.api.config.annotations;

import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.CompoundConfigValue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated string value or each member of a collection of strings matches the specified regex.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Matches {
	/**
	 * @return some regular expression to match against
	 */
	String value();

	final class Processor implements ConfigFieldAnnotationProcessor<Matches> {
		@Override
		@SuppressWarnings("unchecked")
		public void process(Matches matches, MetadataContainerBuilder<?> builder) {
			if (builder instanceof TrackedValue.Builder) {
				Object defaultValue = ((TrackedValue.Builder<?>) builder).getDefaultValue();

				if (defaultValue instanceof String) {
					((TrackedValue.Builder<String>) builder).constraint(Constraint.matching(matches.value()));
				} else if (defaultValue instanceof CompoundConfigValue && ((CompoundConfigValue<?>) defaultValue).getType().equals(String.class)) {
					((TrackedValue.Builder<CompoundConfigValue<String>>) builder).constraint(Constraint.all(Constraint.matching(matches.value())));
				}
			}
		}
	}
}
