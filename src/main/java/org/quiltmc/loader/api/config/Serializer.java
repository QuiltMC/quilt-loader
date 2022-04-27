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

package org.quiltmc.loader.api.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>The entrypoint is exposed with {@code config_serializer} key in the mod json and runs for any environment. It
 * is accessed the first time a config is serialized.
 */
public interface Serializer {
	String getFileExtension();

	void serialize(Config config, OutputStream to) throws IOException;

	void deserialize(Config config, InputStream from);
}
