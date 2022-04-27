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

package org.quiltmc.loader.api.config.values;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.config.util.ConfigUtils;
import org.quiltmc.loader.impl.config.values.ValueListImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiStatus.NonExtendable
public interface ValueList<T> extends List<T>, CompoundConfigValue<T> {
	@SafeVarargs
	static <T> ValueList<T> create(T defaultValue, T... values) {
		ConfigUtils.assertValueType(defaultValue);

		return new ValueListImpl<>(defaultValue, new ArrayList<>(Arrays.asList(values)));
	}
}
