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
