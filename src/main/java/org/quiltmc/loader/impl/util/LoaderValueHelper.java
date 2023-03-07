package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;

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

	public String expectString(LoaderValue.LObject obj, String key) throws T {
		LoaderValue value = obj.get(key);
		if (value == null || value.type() != LType.STRING) {
			throw except("Expected to find '" + key + "' as a string, but got " + value);
		}
		return value.asString();
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
}
