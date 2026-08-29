/*
 * Copyright 2016 FabricMC
 * Copyright 2026 QuiltMC
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

package org.quiltmc.loader.api.plugin;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Logger for plugins. Mods generally won't need this, as they can use whatever logging library the game uses. */
@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface QuiltPluginLogger {
	void error(String msg, Throwable exc);
	void warn(String msg, Throwable exc);
	void info(String msg, Throwable exc);
	void debug(String msg, Throwable exc);
	void trace(String msg, Throwable exc);

	default void error(String msg) { error(msg, (Throwable) null); }
	default void warn(String msg) { warn(msg, (Throwable) null); }
	default void info(String msg) { info(msg, (Throwable) null); }
	default void debug(String msg) { debug(msg, (Throwable) null); }
	default void trace(String msg) { trace(msg, (Throwable) null); }

	void errorFormat(String format, Object... args);
	void warnFormat(String format, Object... args);
	void infoFormat(String format, Object... args);
	void debugFormat(String format, Object... args);
	void traceFormat(String format, Object... args);
}
