package org.quiltmc.loader.impl.metadata.qmj;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModLicense;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public final class ModLicenseImpl implements ModLicense {
	private final String name;
	private final String id;
	private final String url;
	private final String description;
	private static final Map<String, ModLicense> licenses = new HashMap<>();
	private static final Logger LOGGER = LogManager.getLogger(ModLicenseImpl.class);
	static {
		try (JsonReader reader = JsonReader.json(new InputStreamReader(ModLicenseImpl.class.getResourceAsStream("/quilt_loader/licenses.json")))){
			reader.beginObject();
			reader.nextName();// licensesListVersion
			reader.skipValue();
			reader.nextName();// licenses
			JsonLoaderValue.ArrayImpl licenseData = JsonLoaderValue.read(reader).asArray();
			// Technically this wastes memory on holding the things we don't need,
			// but this code is much easier to read and understand than the long-form reader
			for (LoaderValue value : licenseData) {
				LoaderValue.LObject obj = value.asObject();
				String name = obj.get("name").asString();
				String id = obj.get("licenseId").asString();
				String url = obj.get("reference").asString();
				// TODO: description
				licenses.put(id, new ModLicenseImpl(name, id, url, ""));
			}
			 // we can just throw everything else out
		} catch (IOException e) {
			LOGGER.error("Unable to parse license metadata");
			LOGGER.throwing(e);
		}
	}

	public static @Nullable ModLicense fromIdentifier(String identifier) {
		return licenses.get(identifier);
	}

	public static ModLicense fromIdentifierOrDefault(String identifier) {
		ModLicense ret = licenses.get(identifier);
		if (ret == null) {
			return new ModLicenseImpl(identifier, identifier, "", "");
		} else {
			return ret;
		}
	}

	ModLicenseImpl(String name, String id, String url, String description) {
		this.name = name;
		this.id = id;
		this.url = url;
		this.description = description;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String id() {
		return this.id;
	}

	@Override
	public String url() {
		return this.url;
	}

	@Override
	public String description() {
		return this.description;
	}
}
