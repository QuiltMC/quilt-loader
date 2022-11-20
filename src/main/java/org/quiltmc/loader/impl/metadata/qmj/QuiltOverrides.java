package org.quiltmc.loader.impl.metadata.qmj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LArray;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;

/** Parses various overrides. Currently only version and depends/breaks overrides are supported. */
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
				parseV1(rootObject);
				break;
			default: {
				throw parseException(
					schemaVersion, "unsupported schema_version " + version + ", you may need to update loader?"
				);
			}
		}
	}

	private void parseV1(JsonLoaderValue.ObjectImpl rootObject) throws ParseException {
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

			readDepends(overrideObject, true, "depends", mod.dependsOverrides);
			readDepends(overrideObject, false, "breaks", mod.breakOverrides);

			this.overrides.put(pathStr, mod);
		}
	}

	private static void readDepends(LObject obj, boolean isAny, String name, Map<ModDependency, ModDependency> dst) {
		LoaderValue sub = obj.get(name);
		if (sub == null) {
			return;
		}
		if (sub.type() != LType.OBJECT) {
			throw parseException(sub, name + " must be an object!");
		}
		JsonLoaderValue.ObjectImpl subObj = (JsonLoaderValue.ObjectImpl) sub;
		// TODO: Support add OR remove OR replace/with
		JsonLoaderValue replace = subObj.get("replace");
		if (replace == null) {
			throw parseException(sub, "replace is required!");
		}
		JsonLoaderValue with = subObj.get("with");
		if (with == null) {
			throw parseException(sub, "with is required!");
		}
		ModDependency fromDep = V1ModMetadataReader.readDependencyObject(isAny, replace);
		dst.put(fromDep, V1ModMetadataReader.readDependencyObject(isAny, with));
	}

	static ParseException parseException(LoaderValue value, String message) {
		return new ParseException(value.location() + " " + message);
	}

	public static class ModOverrides {
		public String newVersion;
		public final Map<ModDependency, ModDependency> dependsOverrides = new HashMap<>();
		public final Map<ModDependency, ModDependency> breakOverrides = new HashMap<>();
	}
}
