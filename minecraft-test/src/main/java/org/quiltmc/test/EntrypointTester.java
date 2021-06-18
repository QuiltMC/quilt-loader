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

package org.quiltmc.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EntrypointTester implements ModInitializer {


	@Override
	public void onInitialize() {

		Set<CustomEntry> testingInits = new LinkedHashSet<>(FabricLoader.getInstance().getEntrypoints("test:testing", CustomEntry.class));
		System.out.printf("Found %s testing inits%n", testingInits.size());
		System.out.println(testingInits.stream().map(CustomEntry::describe).collect(Collectors.joining(", ")));
	}
}
