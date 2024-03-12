/*
 * Copyright 2022, 2023 QuiltMC
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

import org.jetbrains.annotations.Nullable;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class ModLicenseImpl implements ModLicense {
	private final String name;
	private final String id;
	private final String url;
	private final String description;
	private static final Map<String, ModLicense> LICENSES = new HashMap<>();

	static {
		try (JsonReader reader = JsonReader.json(new InputStreamReader(ModLicenseImpl.class.getClassLoader().getResourceAsStream("quilt_loader/licenses.json")))) {
			JsonLoaderValue object = JsonLoaderValue.read(reader);

			if (object.type() == LoaderValue.LType.OBJECT) {
				JsonLoaderValue.ArrayImpl licenseData = object.asObject().get("licenses").asArray();

				// Technically this wastes memory on holding the things we don't need,
				// but this code is much easier to read and understand than the long-form reader
				for (LoaderValue value : licenseData) {
					LoaderValue.LObject obj = value.asObject();
					String name = obj.get("name").asString();
					String id = obj.get("licenseId").asString();
					String url = obj.get("reference").asString();
					// TODO: description
					LICENSES.put(id, new ModLicenseImpl(name, id, url, ""));
				}
			} else {
				throw new RuntimeException("License file is malformed?");
			}
		} catch (IOException e) {
			Log.error(LogCategory.GENERAL, "Unable to parse license metadata: %s", e);
		}
	}

	public static @Nullable ModLicense fromIdentifier(String identifier) {
		return LICENSES.get(identifier);
	}

	public static ModLicense fromIdentifierOrDefault(String identifier) {
		ModLicense ret = LICENSES.get(identifier);
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
