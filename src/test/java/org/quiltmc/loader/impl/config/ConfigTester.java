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

import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.config.Config;
import org.quiltmc.loader.api.config.ConfigWrapper;
import org.quiltmc.loader.api.config.Constraint;
import org.quiltmc.loader.api.config.TrackedValue;
import org.quiltmc.loader.api.config.annotations.Comment;
import org.quiltmc.loader.api.config.values.ValueList;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

public class ConfigTester {
	@TempDir
	static Path TEMP;

	static TrackedValue<Integer> TEST_INTEGER;
	static TrackedValue<Boolean> TEST_BOOLEAN;
	static TrackedValue<String> TEST_STRING;
	static TrackedValue<ValueList<Integer>> TEST_LIST;

	public ConfigTester() {
		QuiltLoaderImpl.INSTANCE.setGameProvider(new DummyGameProvider(TEMP));
	}

	@Test
	public void testValidation() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig1", builder -> {
				builder.field(TrackedValue.create(new ArrayList<Integer>(), "boop"));
			});
		});

		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig2", builder -> {
				builder.field(TrackedValue.create(ValueList.create(new ArrayList<Integer>()), "boop"));
			});
		});
	}

	@Test
	public void testValues() {
		Config config = Config.create("testmod", "testConfig3", builder -> {
			builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger"));
			builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
			builder.field(TEST_STRING  = TrackedValue.create("blah", "testString"));
			builder.field(TEST_LIST = TrackedValue.create(
					ValueList.create(0, 1, 2, 3, 4), "testList"
			));
			builder.field(TrackedValue.create(LoaderValue.LType.ARRAY, "testEnum"));
			builder.section("testSection", section -> {
				section.metadata(Comment.TYPE, comments -> comments.add("Section comment 1"));
				section.metadata(Comment.TYPE, comments -> comments.add("Section comment 2"));
				section.metadata(Comment.TYPE, comments -> comments.add("Section comment 3"));
				section.field(TrackedValue.create("wooooh", "emote"));
				section.field(TrackedValue.create("etrator", "perp"));
			});
			builder.callback(c -> {
				System.out.println("We was updated!");
			});
		});

		TEST_STRING.registerCallback((value) ->
				System.out.printf("Value '%s' updated: New value: '%s'%n", value.getKey(), value.getValue())
		);

		for (TrackedValue<?> value : config.values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());
		}

		TEST_STRING.setValue("walalala", true);

		System.out.println();

		for (TrackedValue<?> value : config.values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());
		}
	}

	@Test
	public void testMetadata() {
		Config config = Config.create("testmod", "testConfig4", builder -> {
			builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger", creator -> {
				creator.metadata(Comment.TYPE, comments -> comments.add(
						"Comment one",
						"Comment two",
						"Comment three"
				));
			}));
			builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
			builder.field(TEST_STRING  = TrackedValue.create("blah", "testString"));
		});

		for (TrackedValue<?> value : config.values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());

			for (String comment : value.metadata(Comment.TYPE)) {
				System.out.printf("\t// %s%n", comment);
			}
		}
	}

	@Test
	public void testFlags() {
		Config config = Config.create("testmod", "testConfig5", builder -> {
			builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger"));
			builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
			builder.field(TEST_STRING  = TrackedValue.create("blah", "testString"));
		});

		for (TrackedValue<?> value : config.values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());

			for (String comment : value.metadata(Comment.TYPE)) {
				System.out.printf("\t// %s%n", comment);
			}
		}
	}

	@Test
	public void testConstraints() {
		Assertions.assertThrows(RuntimeException.class, () -> Config.create("testmod", "testConfig6", builder -> {
			builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger", creator -> {
				// Should throw an exception since the default value is outside of the constraint range
				creator.constraint(Constraint.range(5, 10));
			}));
			builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
			builder.field(TEST_STRING  = TrackedValue.create("blah", "testString"));
		}));

		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig7", builder -> {
				builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger", creator -> {
					creator.constraint(Constraint.range(-10, 10));
				}));
				builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
				builder.field(TEST_STRING  = TrackedValue.create("blah", "testString"));
			});

			TEST_INTEGER.setValue(1000, true);
		});

		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig8", builder -> {
				builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger", creator -> {
					creator.constraint(Constraint.range(-10, 10));
				}));
				builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
				builder.field(TEST_STRING  = TrackedValue.create("blah", "test", creator -> {
					creator.constraint(Constraint.matching("[a-zA-Z0-9]+:[a-zA-Z0-9]+"));
				}));
			});

			TEST_INTEGER.setValue(1000, true);
		});

		Config.create("testmod", "testConfig9", builder -> {
			builder.field(TEST_INTEGER = TrackedValue.create(0, "testInteger", creator -> {
				creator.constraint(Constraint.range(-10, 10));
			}));
			builder.field(TEST_BOOLEAN = TrackedValue.create(false, "testBoolean"));
			builder.field(TEST_STRING  = TrackedValue.create("test:id", "test", creator -> {
				creator.constraint(Constraint.matching("[a-zA-Z0-9]+:[a-zA-Z0-9]+"));
			}));
		});
	}

	public void testReflectiveConfigs(String id, String format) {
		ConfigWrapper<TestReflectiveConfig> wrapper = Config.create("testmod", id, TestReflectiveConfig.class, builder -> {
			builder.fileType(format);
		});

		for (TrackedValue<?> value : wrapper.getConfig().values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());

			for (String comment : value.metadata(Comment.TYPE)) {
				System.out.printf("\t// %s%n", comment);
			}
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig", TestReflectiveConfig2.class);
		}).printStackTrace();

		Assertions.assertThrows(RuntimeException.class, () -> {
			Config.create("testmod", "testConfig", TestReflectiveConfig3.class);
		}).printStackTrace();
	}

	@Test
	public void testReflectiveConfigs() {
		testReflectiveConfigs("testConfig10", "toml");
		testReflectiveConfigs("testConfig11", "json5");
	}

	@Test
	public void testTomlConfigs() {
		ConfigWrapper<TestReflectiveConfig> wrapper = Config.create("testmod", "testConfig12", TestReflectiveConfig.class, builder -> {
			builder.fileType("toml");
		});

		for (TrackedValue<?> value : wrapper.getConfig().values()) {
			System.out.printf("\"%s\": %s%n", value.getKey(), value.getValue());

			for (String comment : value.metadata(Comment.TYPE)) {
				System.out.printf("\t// %s%n", comment);
			}
		}
	}

	@Test
	public void testValueMapBehavior() {
		TestValueMapConfig c = Config.create("testmod", "testConfig13", TestValueMapConfig.class).getWrapped();

		c.weights.put("" + c.weights.size(), c.weights.size());
	}

	@Test
	public void testValueListBehavior() {
		TestValueListConfig c = Config.create("testmod", "testConfig14", TestValueListConfig.class).getWrapped();

		c.strings.add(c.strings.size() + "");
	}
}
