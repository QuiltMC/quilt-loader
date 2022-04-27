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

import org.junit.jupiter.api.Test;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.MetadataType;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.api.config.values.ValueMap;

public class SerializationTester {
	static TrackedValue<String> TEST;

	@Test
	public void testSerializer() {
		Config config = Config.create("testmod", "testConfig6", builder -> {
			builder.field(TrackedValue.create(0, "testInteger", creator -> {
				creator.metadata(MetadataType.COMMENT, "Comment one");
				creator.metadata(MetadataType.COMMENT, "Comment two");
				creator.metadata(MetadataType.COMMENT, "Comment three");
			}));
			builder.section("super_awesome_section", section1 -> {
				section1.metadata(MetadataType.COMMENT, "This is a section comment!");
				section1.field(TrackedValue.create(1, "before"));
				section1.section("less_awesome_section", section2 -> {
					section2.metadata(MetadataType.COMMENT, "This is another section comment!");
					section2.section("regular_section", section3 -> {
						section3.field(TrackedValue.create(0, "water"));
						section3.field(TrackedValue.create(0, "earth"));
						section3.field(TrackedValue.create(0, "fire", creator -> {
							creator.metadata(MetadataType.COMMENT, "This is a field comment!");
							creator.metadata(MetadataType.COMMENT, "This is another field comment!");

						}));
						section3.field(TrackedValue.create(0, "air"));
					});
					section2.field(TrackedValue.create("lemonade", "crunchy_ice"));
				});
				section1.field(TEST = TrackedValue.create("woot", "after"));
			});
			builder.field(TrackedValue.create(true, "testtt32"));
			builder.field(TrackedValue.create(false, "testBoolean"));
			builder.field(TrackedValue.create("blah", "testString"));
			builder.field(TrackedValue.create(100, "a", "b", "c1", "d"));
			builder.field(TrackedValue.create(1234, "a", "b", "c2"));
			builder.field(TrackedValue.create(
					ValueList.create(0, 1, 2, 3, 4), "testList1"
			));
			builder.field(TrackedValue.create(
					ValueList.create(ValueMap.builder(ValueList.create("")).put("one", ValueList.create("")).build(),
							ValueMap.builder(ValueList.create("")).put("one", ValueList.create("")).build(),
							ValueMap.builder(ValueList.create("")).put("one", ValueList.create("")).build(),
							ValueMap.builder(ValueList.create("")).put("one", ValueList.create("")).build()), "testList2"
			));
		});

		TEST.setValue("This value was set programmatically!", true);
	}
}
