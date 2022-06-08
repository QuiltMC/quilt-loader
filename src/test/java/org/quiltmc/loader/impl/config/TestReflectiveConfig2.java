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
package org.quiltmc.loader.impl.config;

import org.quiltmc.config.api.WrappedConfig;
import org.quiltmc.config.api.annotations.Comment;
import org.quiltmc.config.api.values.ValueList;
import org.quiltmc.config.api.values.ValueMap;

public final class TestReflectiveConfig2 extends WrappedConfig {
	@Comment({"Comment one", "Comment two"})
	public final int a = 0;
	public final int b = 1;
	public final int c = 2;
	public final int d = 3;
	public final String whatever = null;
	public final Nested nested1 = new Nested(10, 11, 12, 13);
	public final Nested nested3 = new Nested(20, 21, 22, 23);
	public final Nested nested4 = new Nested(30, 31, 32, 33);

	public final ValueList<ValueMap<Integer>> listOfNestedObjects = ValueList.create(ValueMap.builder(0).build(),
			ValueMap.builder(0).put("a", 1).put("b", 2).put("c", 3).put("d", 4).build(),
			ValueMap.builder(0).put("a", 1).put("b", 2).put("c", 3).put("d", 4).build(),
			ValueMap.builder(0).put("a", 1).put("b", 2).put("c", 3).put("d", 4).build()
	);

	public static final class Nested {
		public final int a;
		public final int b;
		public final int c;
		public final int d;

		public Nested(int a, int b, int c, int d) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
		}
	}
}
