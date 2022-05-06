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

package org.quiltmc.loader.api.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.quiltmc.loader.api.config.Comments;
import org.quiltmc.loader.api.config.MetadataContainerBuilder;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.impl.config.CommentsImpl;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Comment {
	MetadataType<Comments, Builder> TYPE = MetadataType.create(Comments.class, () -> Optional.of(new CommentsImpl(Collections.emptyList())), Builder::new);

	String[] value();

	final class Processor implements ConfigFieldAnnotationProcessor<Comment> {
		@Override
		public void process(Comment comment, MetadataContainerBuilder<?> builder) {
			for (String c : comment.value()) {
				builder.metadata(TYPE, comments -> comments.add(c));
			}
		}
	}

	final class Builder implements MetadataType.Builder<Comments> {
		private final List<String> comments = new ArrayList<>(0);

		public void add(String... comments) {
			this.comments.addAll(Arrays.asList(comments));
		}

		@Override
		public Comments build() {
			return new CommentsImpl(this.comments);
		}
	}
}
