/*
 * Copyright 2021 QuiltMC
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

package org.quiltmc.loader.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.DefaultLanguageAdapter;

public final class DefaultLanguageAdapterTest {
	public static final Entrance FIELD = () -> "field";

	public static String staticMethod() {
		return "static method";
	}

	public String instanceMethod() {
		return "instance method";
	}

	@BeforeAll
	public static void prepare() {
		new QuiltLauncherBase() {
			@Override
			public void propose(URL url) {
			}

			@Override
			public EnvType getEnvironmentType() {
				return EnvType.SERVER;
			}

			@Override
			public boolean isClassLoaded(String name) {
				return false;
			}

			@Override
			public InputStream getResourceAsStream(String name) {
				return null;
			}

			@Override
			public ClassLoader getTargetClassLoader() {
				return DefaultLanguageAdapterTest.class.getClassLoader();
			}

			@Override
			public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
				return new byte[0];
			}

			@Override
			public boolean isDevelopment() {
				return true;
			}

			@Override
			public String getEntrypoint() {
				return null;
			}

			@Override
			public String getTargetNamespace() {
				return null;
			}

			@Override
			public Collection<URL> getLoadTimeDependencies() {
				return null;
			}
		};
	}

	@Test
	public void test() throws Exception {
		// default language adapter does not use the mod container
		DefaultLanguageAdapter adapter = DefaultLanguageAdapter.INSTANCE;
		Entrance classEntrance = adapter.create(null, "org.quiltmc.loader.test.DefaultLanguageAdapterTest$EntranceImpl", Entrance.class);
		Entrance fieldEntrance = adapter.create(null, "org.quiltmc.loader.test.DefaultLanguageAdapterTest::FIELD", Entrance.class);
		Entrance staticMethodEntrance = adapter.create(null, "org.quiltmc.loader.test.DefaultLanguageAdapterTest::staticMethod", Entrance.class);
		Entrance instanceMethodEntrance = adapter.create(null, "org.quiltmc.loader.test.DefaultLanguageAdapterTest::instanceMethod", Entrance.class);

		// testing object calls
		Set<Entrance> entrances = new HashSet<>();
		entrances.add(classEntrance);
		entrances.add(fieldEntrance);
		entrances.add(staticMethodEntrance);
		entrances.add(instanceMethodEntrance);

		// testing "describe" default method call
		assertEquals(
				"This is \"class\", This is \"field\", This is \"instance method\", This is \"static method\"",
				entrances.stream()
						.sorted(Comparator.comparing(Entrance::name))
						.map(Entrance::describe)
						.collect(Collectors.joining(", "))
		);
	}

	public static final class EntranceImpl implements Entrance {
		@Override
		public String name() {
			return "class";
		}
	}

	public interface Entrance {
		String name();

		default String describe() {
			return "This is \"" + name() + "\"";
		}
	}
}
