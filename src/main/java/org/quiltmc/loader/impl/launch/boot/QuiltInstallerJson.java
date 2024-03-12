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

package org.quiltmc.loader.impl.launch.boot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.plugin.LoaderValueFactory;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class QuiltInstallerJson {

	final List<QuiltInstallerLibrary> libraries = new ArrayList<>();

	QuiltInstallerJson(InputStream stream) throws IOException {
		LoaderValue lValue = LoaderValueFactory.getFactory().read(stream);
		if (lValue.type() != LType.OBJECT) {
			throw new IOException("Expected the document to be an object, but was " + lValue.type());
		}
		LObject root = lValue.asObject();
		LoaderValue libs = root.get("libraries");
		if (libs == null || libs.type() != LType.OBJECT) {
			throw new IOException("Expected to find 'libraries', but found " + libs);
		}
		// Loader doesn't have client or server-specific libraries
		LoaderValue allLibs = libs.asObject().get("common");
		if (allLibs == null || allLibs.type() != LType.ARRAY) {
			throw new IOException("Expected to find 'libraries.common', but found " + allLibs);
		}

		for (LoaderValue lib : allLibs.asArray()) {
			libraries.add(new QuiltInstallerLibrary(lib));
		}
	}

	static class QuiltInstallerLibrary {
		final String name;

		QuiltInstallerLibrary(LoaderValue from) throws IOException {
			if (from.type() != LType.OBJECT) {
				throw new IOException("Expected library to be an object, but got " + from);
			}
			LoaderValue nameValue = from.asObject().get("name");
			if (nameValue == null || nameValue.type() != LType.STRING) {
				throw new IOException("Expected to find 'name', but found " + nameValue + " in " + from);
			}
			name = nameValue.asString();
		}
	}
}
