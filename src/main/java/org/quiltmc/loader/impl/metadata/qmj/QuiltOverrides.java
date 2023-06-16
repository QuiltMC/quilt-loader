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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LArray;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Parses various overrides. Currently only version and depends/breaks overrides are supported. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class QuiltOverrides {

	public final Map<String, ModOverrides> overrides = new HashMap<>();

	/** Loads overrides from the given json file. */
	public QuiltOverrides(Path file) throws IOException, ParseException {
		if (!Files.isRegularFile(file)) {
			return;
		}
		JsonLoaderValue root = JsonLoaderValue.read(JsonReader.json(file));
		if (root.type() != LType.OBJECT) {
			throw new IOException("Expected to find an object, but got " + root.type());
		}
		JsonLoaderValue.ObjectImpl rootObject = root.asObject();
		JsonLoaderValue schemaVersion = rootObject.get("schema_version");

		if (schemaVersion == null) {
			throw new ParseException("No schema_version field was found!");
		}

		if (schemaVersion.type() != LType.NUMBER) {
			throw parseException(schemaVersion, "schema_version must be a number");
		}

		int version = schemaVersion.asNumber().intValue();
		switch (version) {
			case 1:
				parseV1(file, rootObject);
				break;
			default: {
				throw parseException(
					schemaVersion, "unsupported schema_version " + version + ", you may need to update loader?"
				);
			}
		}
	}

	private void parseV1(Path file, JsonLoaderValue.ObjectImpl rootObject) throws ParseException {
		// "overrides": array of objects:
		// // "path": full path (Like <game>/mods/buildcraft-9.0.0.jar!libblockattributes-1.0.0.jar)
		// // "version": // new version
		// // "depends" / "breaks": array of object:
		// // // "replace": a full json object which exactly equals the previous dependency
		// // // "with": The replacement json object
		JsonLoaderValue overrides = rootObject.get("overrides");
		if (overrides == null) {
			// Bit odd, but okay
			return;
		}
		if (overrides.type() != LType.ARRAY) {
			throw parseException(overrides, "overrides must be an array of objects!");
		}

		JsonLoaderValue.ArrayImpl overridesArray = overrides.asArray();
		for (LoaderValue override : overridesArray) {
			if (override.type() != LType.OBJECT) {
				throw parseException(override, "overrides must be an array of objects!");
			}
			JsonLoaderValue.ObjectImpl overrideObject = (JsonLoaderValue.ObjectImpl) override;
			JsonLoaderValue path = overrideObject.get("path");
			if (path == null) {
				throw parseException(override, "path is required");
			}
			if (path.type() != LType.STRING) {
				throw parseException(path, "path must be a string");
			}
			String pathStr = path.asString();
			ModOverrides mod = new ModOverrides();

			LoaderValue version = overrideObject.get("version");
			if (version != null) {
				if (version.type() != LType.STRING) {
					throw parseException(version, "version must be a string!");
				}
				mod.newVersion = version.asString();
			}

			readDepends(file, overrideObject, true, "depends", mod.dependsOverrides);
			readDepends(file, overrideObject, false, "breaks", mod.breakOverrides);

			this.overrides.put(pathStr, mod);
		}
	}

	private static void readDepends(Path file, LObject obj, boolean isAny, String name, SpecificOverrides dst) {
		LoaderValue sub = obj.get(name);
		if (sub == null) {
			return;
		}
		if (sub.type() == LType.ARRAY) {
			LArray array = sub.asArray();
			for (int i = 0; i < array.size(); i++) {
				readSingleDepends(file, array.get(i), isAny, dst);
			}
		} else if (sub.type() == LType.OBJECT) {
			readSingleDepends(file, sub, isAny, dst);
		} else {
			throw parseException(sub, "Must be either an object or an array of objects!");
		}
	}

	private static final int ADD = 1 << 0;
	private static final int REMOVE = 1 << 1;
	private static final int REPLACE = 1 << 2;
	private static final int WITH = 1 << 3;

	private static void readSingleDepends(Path file, LoaderValue value, boolean isAny, SpecificOverrides dst) {
		if (value.type() != LType.OBJECT) {
			throw parseException(value, "Must be an object!");
		}
		JsonLoaderValue.ObjectImpl subObj = (JsonLoaderValue.ObjectImpl) value;

		JsonLoaderValue add = subObj.get("add");
		JsonLoaderValue remove = subObj.get("remove");
		JsonLoaderValue replace = subObj.get("replace");
		JsonLoaderValue with = subObj.get("with");

		int flags = (add != null ? ADD : 0) //
			| (remove != null ? REMOVE : 0) //
			| (replace != null ? REPLACE : 0) //
			| (with != null ? WITH : 0);

		if (flags == ADD) {

			dst.additions.add(V1ModMetadataReader.readDependencyObject(file, isAny, add));

		} else if (flags == REMOVE) {

			dst.removals.add(V1ModMetadataReader.readDependencyObject(file, isAny, remove));

		} else if (flags == (REPLACE | WITH)) {

			ModDependency fromDep = V1ModMetadataReader.readDependencyObject(file, isAny, replace);
			dst.replacements.put(fromDep, V1ModMetadataReader.readDependencyObject(file, isAny, with));

		} else {
			throw parseException(
				value, "Expected either: 'add', or 'remove', or both 'replace' and 'with', but got "//
					+ (add != null ? "'add', " : "") + (remove != null ? "'remove', " : "")//
					+ (replace != null ? "'replace', " : "") + (with != null ? "'with', " : "")//
			);
		}
	}

	static ParseException parseException(LoaderValue value, String message) {
		return new ParseException(value.location() + " " + message);
	}

	public static class ModOverrides {
		public String newVersion;
		public final SpecificOverrides dependsOverrides = new SpecificOverrides();
		public final SpecificOverrides breakOverrides = new SpecificOverrides();
	}

	public static class SpecificOverrides {
		public final Map<ModDependency, ModDependency> replacements = new HashMap<>();
		public final List<ModDependency> additions = new ArrayList<>();
		public final List<ModDependency> removals = new ArrayList<>();
	}
}
