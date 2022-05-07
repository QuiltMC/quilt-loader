/*
 * Copyright 2016 FabricMC
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

import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;

public final class TestReflectiveConfig3 {
	@Comment({"Comment one", "Comment two"})
	public final int a = 0;
	public final int b = 1;
	public final int c = 2;
	public final int d = 3;
	public final Vec3i vec = new Vec3i(100, 200, 300);
	public final String whatever = "Riesling";
	public final Nested nested1 = new Nested(10, 11, 12, 13);
	public final Nested nested3 = new Nested(20, 21, 22, 23);
	public final ValueList<Vec3i> vecs = ValueList.create(new Vec3i(0, 0, 0),
			new Vec3i(1, 2, 3),
			new Vec3i(4, 5, 6),
			new Vec3i(7, 8, 9)
	);

	@Comment("Test section comment")
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
