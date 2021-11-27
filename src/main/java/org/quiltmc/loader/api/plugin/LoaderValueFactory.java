package org.quiltmc.loader.api.plugin;

import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderFactoryImpl;

/** Location-less factories */
public interface LoaderValueFactory {

	public static LoaderValueFactory getFactory() {
		return JsonLoaderFactoryImpl.INSTANCE;
	}

	LoaderValue nul();

	LoaderValue bool(boolean value);

	LoaderValue number(Number value);

	LoaderValue string(String value);

	LoaderValue array(LoaderValue[] values);

	LoaderValue object(Map<String, LoaderValue> map);
}
