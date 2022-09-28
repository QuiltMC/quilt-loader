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

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;

public final class JsonLoaderFactoryImpl implements LoaderValueFactory {

	public static final JsonLoaderFactoryImpl INSTANCE = new JsonLoaderFactoryImpl();
	private static final String LOCATION = "not-from-file";

	private JsonLoaderFactoryImpl() {}

	@Override
	public LoaderValue nul() {
		return new JsonLoaderValue.NullImpl(LOCATION);
	}

	@Override
	public LoaderValue bool(boolean value) {
		return new JsonLoaderValue.BooleanImpl(LOCATION, value);
	}

	@Override
	public LoaderValue number(Number value) {
		return new JsonLoaderValue.NumberImpl(LOCATION, value);
	}

	@Override
	public LoaderValue string(String value) {
		return new JsonLoaderValue.StringImpl(LOCATION, value);
	}

	@Override
	public LoaderValue array(LoaderValue[] values) {
		return new JsonLoaderValue.ArrayImpl(LOCATION, Arrays.asList(Arrays.copyOf(values, values.length)));
	}

	@Override
	public LoaderValue object(Map<String, LoaderValue> map) {
		return new JsonLoaderValue.ObjectImpl(LOCATION, new HashMap<>(map));
	}
}
