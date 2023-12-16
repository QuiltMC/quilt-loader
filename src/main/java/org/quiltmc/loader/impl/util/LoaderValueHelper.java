/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class LoaderValueHelper<T extends Throwable> {

	public static final LoaderValueHelper<IOException> IO_EXCEPTION;

	static {
		IO_EXCEPTION = new LoaderValueHelper<>(IOException::new);
	}

	final Function<String, T> exception;

	public LoaderValueHelper(Function<String, T> exception) {
		this.exception = exception;
	}

	@NotNull
	private T except(String msg) throws T {
		T ex = exception.apply(msg);
		if (ex == null) {
			throw new IllegalStateException("Expected the function to return a not-null exception while throwing '" + msg + "'");
		}
		return ex;
	}

	public LoaderValue expectValue(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null) {
			throw except("Expected to find '" + key + "' , but it was missing!");
		}
		return value;
	}

	public String expectString(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.STRING) {
			throw except("Expected to find '" + key + "' as a string, but got " + value);
		}
		return value.asString();
	}

	public <E extends Enum<E>> E expectEnum(Class<E> clazz, LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.STRING) {
			throw except("Expected to find '" + key + "' as a string, but got " + value);
		}
		return expectEnum(clazz, value);
	}

	public String expectStringOrNull(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null) {
			throw except("Expected to find '" + key + "' as a string or a null, but it was missing!");
		}
		switch (value.type()) {
			case STRING: {
				return value.asString();
			}
			case NULL: {
				return null;
			}
			default: {
				throw except("Expected to find '" + key + "' as a string, but got " + value);
			}
		}
	}

	public boolean expectBoolean(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.BOOLEAN) {
			throw except("Expected to find '" + key + "' as a string, but got " + value);
		}
		return value.asBoolean();
	}

	public Number expectNumber(LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.NUMBER) {
			throw except("Expected to find '" + key + "' as a number, but got " + value);
		}
		return value.asNumber();
	}

	public LoaderValue.LArray expectArray(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.ARRAY) {
			throw except("Expected to find '" + key + "' as an array, but got " + value);
		}
		return value.asArray();
	}

	public LoaderValue.LObject expectObject(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.OBJECT) {
			throw except("Expected to find '" + key + "' as an object, but got " + value);
		}
		return value.asObject();
	}

	public LoaderValue.LObject expectObject(LoaderValue value) throws T {
		if (value == null || value.type() != LType.OBJECT) {
			throw except("Expected " + value + " to be an object!");
		}
		return value.asObject();
	}

	public String expectString(LoaderValue value) throws T {
		if (value == null || value.type() != LType.STRING) {
			throw except("Expected " + value + " to be a string!");
		}
		return value.asString();
	}

	public <E extends Enum<E>> E expectEnum(Class<E> clazz, LoaderValue value) throws T {
		String str = expectString(value);
		for (E val : clazz.getEnumConstants()) {
			if (str.equals(val.name())) {
				return val;
			}
		}
		throw except("Expected " + str + " to be one of " + Arrays.toString(clazz.getEnumConstants()));
	}
}
