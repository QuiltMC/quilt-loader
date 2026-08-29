/*
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

package org.quiltmc.loader.impl.plugin;

import org.quiltmc.loader.api.plugin.QuiltPluginLogger;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class PluginLoggerImpl implements QuiltPluginLogger {
	final LogCategory category;

	PluginLoggerImpl(String name) {
		this.category = LogCategory.create("Plugin", name);
	}

	@Override
	public void error(String msg, Throwable exc) {
		Log.error(category, msg, exc);
	}

	@Override
	public void warn(String msg, Throwable exc) {
		Log.warn(category, msg, exc);
	}

	@Override
	public void info(String msg, Throwable exc) {
		Log.info(category, msg, exc);
	}

	@Override
	public void debug(String msg, Throwable exc) {
		Log.debug(category, msg, exc);
	}

	@Override
	public void trace(String msg, Throwable exc) {
		Log.trace(category, msg, exc);
	}

	@Override
	public void errorFormat(String format, Object... args) {
		Log.error(category, format, args);
	}

	@Override
	public void warnFormat(String format, Object... args) {
		Log.warn(category, format, args);
	}

	@Override
	public void infoFormat(String format, Object... args) {
		Log.info(category, format, args);
	}

	@Override
	public void debugFormat(String format, Object... args) {
		Log.debug(category, format, args);
	}

	@Override
	public void traceFormat(String format, Object... args) {
		Log.trace(category, format, args);
	}
}
