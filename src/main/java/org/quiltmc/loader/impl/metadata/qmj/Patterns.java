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

import java.util.regex.Pattern;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/**
 * Patterns used to match strings specified in {@code quilt.mod.json} files.
 */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
final class Patterns {
	public static final Pattern VALID_MOD_ID = Pattern.compile("^[a-z][a-z0-9-_]{1,63}$");
	public static final Pattern VALID_MAVEN_GROUP = Pattern.compile("^[a-zA-Z0-9-_.]+$");
	public static final Pattern VALID_INTERMEDIATE = Pattern.compile("^[a-zA-Z0-9-_.]+:[a-zA-Z0-9-_.]+$");
	public static final Pattern VERSION_PLACEHOLDER = Pattern.compile("^[a-zA-Z0-9-_.]+$");

	private Patterns() {
	}
}
