/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.transformer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.quiltmc.loader.impl.launch.knot.IllegalQuiltInternalAccessError;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltInternalExceptionUtil {

	private static final ConcurrentMap<String, Boolean> WARNED_ENTRIES = new ConcurrentHashMap<>();

	public static void throwInternalAccess(String msg) {
		throw new IllegalQuiltInternalAccessError(msg);
	}

	public static void warnInternalAccess(String msg) {
		if (WARNED_ENTRIES.put(msg, Boolean.TRUE) == null) {
			Log.warn(LogCategory.GENERAL, msg);
		}
	}
}
