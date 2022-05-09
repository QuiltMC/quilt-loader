package org.quiltmc.loader.impl.config;

import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.annotations.ConfigFieldAnnotationProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Containing class for multiple {@link Comment} annotations.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Comments {
	Comment[] value();

	final class Processor implements ConfigFieldAnnotationProcessor<Comments> {
		@Override
		public void process(Comments comments, MetadataContainerBuilder<?> builder) {
			for (Comment comment : comments.value()) {
				for (String c : comment.value()) {
					builder.metadata(Comment.TYPE, b -> b.add(c));
				}
			}
		}
	}
}
